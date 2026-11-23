"""ЛР6: персоналізація за виправленнями користувача.

Три окремі кроки:
  1. prepare()     — підготовка даних: нормалізація прикладів, вилучення суми, дедуплікація;
  2. best_match()  — AI-обробка: пошук найближчого прикладу в TF-IDF-просторі моделі (косинусна схожість);
  3. decide()      — перетворення результату: правило, коли категорія користувача замінює прогноз моделі.
Модель не перенавчається, приклади не зберігаються на сервері.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Callable, Iterable, Optional

from .classifier import Prediction
from .parser import parse


@dataclass(frozen=True)
class PreparedExample:
    text: str        # як ввів користувач (для пояснення)
    cleaned: str     # без суми й валюти, нижній регістр (для порівняння)
    category: str


@dataclass(frozen=True)
class Match:
    example: PreparedExample
    similarity: float


@dataclass(frozen=True)
class Decision:
    category: str
    confidence: float
    source: str                     # "model" | "personal"
    low_confidence: bool
    alternatives: list[tuple[str, float]]
    match: Optional[Match]          # не None лише коли персоналізацію застосовано


def prepare(examples: Iterable) -> list[PreparedExample]:
    """Приклади мають атрибути text і category (PersonalExample). Перший однаковий опис перемагає
    (клієнт надсилає новіші першими), приклади з < 3 літер після вилучення суми — відкидаються."""
    seen: set[str] = set()
    prepared: list[PreparedExample] = []
    for ex in examples:
        cleaned = parse(ex.text).cleaned_text.lower().strip()
        if sum(ch.isalpha() for ch in cleaned) < 3 or cleaned in seen:  # «на 500» → «на»: замало змісту
            continue
        seen.add(cleaned)
        prepared.append(PreparedExample(text=ex.text, cleaned=cleaned, category=ex.category))
    return prepared


def best_match(query_cleaned: str, examples: list[PreparedExample], embed: Callable[[list[str]], object]) -> Optional[Match]:
    if not examples:
        return None
    matrix = embed([e.cleaned for e in examples])
    query = embed([query_cleaned.lower()])
    sims = (matrix @ query.T).toarray().ravel()
    i = int(sims.argmax())
    return Match(example=examples[i], similarity=float(min(1.0, max(0.0, sims[i]))))


def decide(prediction: Prediction, model_low: bool, match: Optional[Match], threshold: float) -> Decision:
    """Категорія користувача перемагає, якщо збіг достатньо схожий і (категорії різні або модель невпевнена)."""
    model_alternatives = list(prediction.alternatives)
    applies = (
        match is not None
        and match.similarity >= threshold
        and (match.example.category != prediction.category or model_low)
    )
    if applies:
        chosen = match.example.category
        alternatives = [(prediction.category, prediction.confidence)] + model_alternatives
        alternatives = [(c, p) for c, p in alternatives if c != chosen]
        return Decision(chosen, match.similarity, "personal", False, alternatives, match)

    category = "other" if model_low else prediction.category
    alternatives = ([(prediction.category, prediction.confidence)] if model_low else []) + model_alternatives
    return Decision(category, prediction.confidence, "model", model_low, alternatives, None)
