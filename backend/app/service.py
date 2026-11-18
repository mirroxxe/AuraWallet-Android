"""Бізнес-логіка: поєднує виділення сутностей і AI-класифікатор в одну відповідь."""
from __future__ import annotations

import logging
import time

from .ai.classifier import AIUnavailableError, TransactionClassifier
from .ai.parser import parse
from .categories import INCOME_CATEGORIES, label
from .config import Settings
from .schemas import Alternative, CategorizeRequest, CategorizeResponse

log = logging.getLogger("aurawallet")


def _explain(category_label: str, confidence: float, keywords: list[str], merchant: str | None,
             low: bool, locale: str) -> str:
    pct = round(confidence * 100)
    if locale == "en":
        if low:
            return f"Not sure about the category ({pct}%). Please pick it manually."
        reason = f"keywords: {', '.join(keywords)}" if keywords else "similar descriptions"
        where = f" Merchant: {merchant}." if merchant else ""
        return f"Looks like {category_label} ({pct}%) based on {reason}.{where}"
    if low:
        return f"Модель не впевнена в категорії ({pct}%). Оберіть категорію вручну."
    reason = "ключових слів «" + "», «".join(keywords) + "»" if keywords else "схожих описів"
    where = f" Магазин: {merchant}." if merchant else ""
    return f"Схоже на «{category_label}» ({pct}%) на основі {reason}.{where}"


def categorize(req: CategorizeRequest, classifier: TransactionClassifier | None, settings: Settings) -> CategorizeResponse:
    started = time.perf_counter()
    if classifier is None or settings.simulate_ai_failure:
        raise AIUnavailableError("classifier is not available")

    entities = parse(req.text)
    try:
        prediction = classifier.predict(entities.cleaned_text)
    except Exception as exc:  # будь-яка внутрішня помилка моделі → контрольована відповідь
        log.exception("AI prediction failed")
        raise AIUnavailableError(str(exc)) from exc

    low = prediction.confidence < settings.min_confidence
    category = "other" if low else prediction.category
    alternatives = [(prediction.category, prediction.confidence)] if low else []
    alternatives += prediction.alternatives

    return CategorizeResponse(
        category=category,
        label=label(category, req.locale),
        confidence=round(prediction.confidence, 3),
        is_income=category in INCOME_CATEGORIES,
        amount=entities.amount,
        currency=entities.currency,
        merchant=entities.merchant,
        explanation=_explain(label(prediction.category, req.locale), prediction.confidence,
                             prediction.keywords, entities.merchant, low, req.locale),
        alternatives=[Alternative(category=c, label=label(c, req.locale), confidence=round(p, 3))
                      for c, p in alternatives[:3]],
        low_confidence=low,
        processing_ms=round((time.perf_counter() - started) * 1000, 2),
    )
