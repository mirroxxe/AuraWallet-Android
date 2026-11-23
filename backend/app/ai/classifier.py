"""AI-компонент: класифікація опису операції за категорією.

Модель — TF-IDF (символьні n-грами + слова) і логістична регресія зі scikit-learn.
Навчається під час запуску сервісу на невеликому наборі прикладів (training_data.csv),
тому не потребує зовнішніх API та ключів і працює офлайн.
"""
from __future__ import annotations

import csv
import time
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import FeatureUnion, Pipeline

DATA_PATH = Path(__file__).with_name("training_data.csv")
# Службові слова не показуємо в поясненні.
_STOPWORDS = {"за", "на", "до", "від", "для", "або", "the", "for", "and", "at", "to"}


class AIUnavailableError(RuntimeError):
    """AI-компонент не зміг обробити запит."""


@dataclass
class Prediction:
    category: str
    confidence: float
    alternatives: list[tuple[str, float]]
    keywords: list[str]


class TransactionClassifier:
    def __init__(self, data_path: Path = DATA_PATH) -> None:
        texts, labels = self._load(data_path)
        self._words = TfidfVectorizer(analyzer="word", ngram_range=(1, 2), lowercase=True,
                                      token_pattern=r"(?u)\b[\w'ʼ&.]+\b")
        self._pipeline = Pipeline([
            ("features", FeatureUnion([
                ("chars", TfidfVectorizer(analyzer="char_wb", ngram_range=(2, 5), lowercase=True, sublinear_tf=True)),
                ("words", self._words),
            ])),
            ("model", LogisticRegression(C=12.0, max_iter=3000)),
        ])
        started = time.perf_counter()
        self._pipeline.fit(texts, labels)
        self.train_ms = (time.perf_counter() - started) * 1000
        self.classes: list[str] = list(self._pipeline.named_steps["model"].classes_)
        self.samples = len(texts)
        # Індекс, з якого в об'єднаному векторі починаються ознаки-слова (для пояснення).
        self._word_offset = len(self._pipeline.named_steps["features"].transformer_list[0][1].vocabulary_)

    @staticmethod
    def _load(path: Path) -> tuple[list[str], list[str]]:
        with path.open(encoding="utf-8") as fh:
            rows = list(csv.DictReader(fh))
        return [r["text"] for r in rows], [r["category"] for r in rows]

    def predict(self, text: str, top_k: int = 3) -> Prediction:
        probs = self._pipeline.predict_proba([text])[0]
        order = np.argsort(probs)[::-1]
        best = order[0]
        alternatives = [(self.classes[i], float(probs[i])) for i in order[1:top_k]]
        return Prediction(
            category=self.classes[best],
            confidence=float(probs[best]),
            alternatives=alternatives,
            keywords=self._keywords(text, best),
        )

    def embed(self, texts: list[str]):
        """ЛР6: символьні TF-IDF-вектори (L2-нормовані) у тому ж просторі ознак, на якому навчена модель.
        Скалярний добуток двох векторів = косинусна схожість."""
        chars = self._pipeline.named_steps["features"].transformer_list[0][1]
        return chars.transform(texts)

    def _keywords(self, text: str, class_index: int, limit: int = 3) -> list[str]:
        """Слова з тексту, які найбільше «тягнуть» до обраної категорії."""
        vocab = self._words.vocabulary_
        coefs = self._pipeline.named_steps["model"].coef_[class_index]
        tokens = self._words.build_analyzer()(text)
        scored = []
        for token in set(tokens):
            if any(part in _STOPWORDS or len(part) < 3 for part in token.split()):
                continue
            idx = vocab.get(token)
            if idx is not None:
                weight = coefs[self._word_offset + idx]
                if weight > 0:
                    scored.append((weight, token))
        return [t for _, t in sorted(scored, reverse=True)[:limit]]
