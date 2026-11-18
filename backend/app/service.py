"""Бізнес-логіка: поєднує виділення сутностей і AI-класифікатор в одну відповідь."""
from __future__ import annotations

import calendar
import logging
import time
from datetime import date

from .ai.classifier import AIUnavailableError, TransactionClassifier
from .ai.parser import parse
from .categories import INCOME_CATEGORIES, label
from .config import Settings
from .schemas import (Alternative, BudgetSummaryRequest, BudgetSummaryResponse, CategorizeRequest,
                      CategorizeResponse, CategorySpend)

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


# ---------- Бюджет місяця (ЛР3) ----------
WARNING_THRESHOLD = 80.0   # як у iOS-версії: сповіщення на 80% і 100%
EXCEEDED_THRESHOLD = 100.0


def _uah(value: float) -> str:
    return "₴" + f"{value:,.0f}".replace(",", " ")


def _budget_message(status: str, spent: float, budget: float, remaining: float, days_left: int,
                    daily: float, locale: str) -> str:
    if locale == "en":
        if status == "exceeded":
            return f"Monthly budget is used up: spent {_uah(spent)} of {_uah(budget)}."
        if status == "warning":
            return f"80% of the budget is used: {_uah(remaining)} left for {days_left} days — about {_uah(daily)} a day."
        return f"On track: about {_uah(daily)} a day until the end of the month."
    if status == "exceeded":
        return f"Місячний бюджет вичерпано: витрачено {_uah(spent)} з {_uah(budget)}."
    if status == "warning":
        return f"Використано 80% бюджету: лишилось {_uah(remaining)} на {days_left} дн. — приблизно {_uah(daily)} на день."
    return f"У межах бюджету: приблизно {_uah(daily)} на день до кінця місяця."


def budget_summary(req: BudgetSummaryRequest) -> BudgetSummaryResponse:
    today = req.today or date.today()
    days_in_month = calendar.monthrange(today.year, today.month)[1]
    days_left = days_in_month - today.day + 1

    expenses = [t for t in req.transactions if not t.is_income]
    spent = round(sum(t.amount for t in expenses), 2)
    income = round(sum(t.amount for t in req.transactions if t.is_income), 2)
    remaining = round(req.monthly_budget - spent, 2)
    raw_percent = spent / req.monthly_budget * 100
    # Статус рахуємо з неокругленого значення: 799.9 з 1000 — ще «ok», хоча округлено це 80.0%.
    status = "exceeded" if raw_percent >= EXCEEDED_THRESHOLD else "warning" if raw_percent >= WARNING_THRESHOLD else "ok"
    percent = round(raw_percent, 1)
    daily = round(max(0.0, remaining) / days_left, 2)

    totals: dict[str, float] = {}
    for t in expenses:
        totals[t.category] = totals.get(t.category, 0.0) + t.amount
    by_category = [
        CategorySpend(category=c, label=label(c, req.locale), amount=round(a, 2), share=round(a / spent, 4) if spent else 0.0)
        for c, a in sorted(totals.items(), key=lambda kv: kv[1], reverse=True)
    ]

    return BudgetSummaryResponse(
        monthly_budget=req.monthly_budget, spent=spent, income=income, remaining=remaining,
        percent_used=percent, status=status, days_left=days_left, daily_allowance=daily,
        by_category=by_category,
        message=_budget_message(status, spent, req.monthly_budget, remaining, days_left, daily, req.locale),
        counted_transactions=len(expenses),
    )
