"""Pydantic-моделі запиту й відповіді API."""
from __future__ import annotations

from datetime import date
from typing import List, Literal, Optional

from pydantic import BaseModel, ConfigDict, Field, field_validator


class CategorizeRequest(BaseModel):
    """Опис операції, який користувач увів у мобільному застосунку."""

    model_config = ConfigDict(json_schema_extra={"example": {"text": "Кава в Aroma Kava 85 грн", "locale": "uk"}})

    text: str = Field(..., description="Опис операції довільним текстом", min_length=1, max_length=200)
    locale: Literal["uk", "en"] = Field("uk", description="Мова підписів у відповіді")

    @field_validator("text")
    @classmethod
    def text_must_have_letters(cls, value: str) -> str:
        value = " ".join(value.split())
        if len(value) < 2:
            raise ValueError("Опис занадто короткий: введіть хоча б 2 символи")
        if not any(ch.isalpha() for ch in value):
            raise ValueError("Опис має містити хоча б одне слово, а не лише цифри")
        return value


class Alternative(BaseModel):
    category: str
    label: str
    confidence: float = Field(..., ge=0, le=1)


class CategorizeResponse(BaseModel):
    """Результат розпізнавання: категорія, сума, магазин і пояснення."""

    category: str = Field(..., description="Код категорії, напр. coffee")
    label: str = Field(..., description="Назва категорії мовою locale")
    confidence: float = Field(..., ge=0, le=1, description="Впевненість моделі 0..1")
    is_income: bool
    amount: Optional[float] = Field(None, description="Сума, якщо знайдена в тексті")
    currency: Optional[str] = Field(None, description="ISO-код валюти: UAH, USD, EUR, PLN")
    merchant: Optional[str] = Field(None, description="Відомий магазин або сервіс, якщо знайдено")
    explanation: str
    alternatives: List[Alternative] = []
    low_confidence: bool = Field(False, description="true, якщо модель невпевнена й варто обрати категорію вручну")
    processing_ms: float


class HealthResponse(BaseModel):
    status: Literal["ok", "degraded"]
    version: str
    model_loaded: bool
    model_classes: int
    training_samples: int


class ErrorBody(BaseModel):
    code: str
    message: str
    field: Optional[str] = None


class ErrorResponse(BaseModel):
    error: ErrorBody


# ---------- Бюджет місяця (ЛР3) ----------

class BudgetTransaction(BaseModel):
    """Збережена операція, яку клієнт передає для розрахунку бюджету."""

    amount: float = Field(..., gt=0, le=10_000_000, description="Сума в гривнях, завжди додатна")
    category: str = Field(..., description="Код категорії з categories.py")
    is_income: bool = Field(False, description="true — дохід, не рахується у витрати")

    @field_validator("category")
    @classmethod
    def category_must_be_known(cls, value: str) -> str:
        from .categories import CATEGORY_LABELS  # локальний імпорт, щоб уникнути циклу
        if value not in CATEGORY_LABELS:
            raise ValueError(f"Невідома категорія «{value}»")
        return value


class BudgetSummaryRequest(BaseModel):
    model_config = ConfigDict(json_schema_extra={"example": {
        "monthly_budget": 18000, "today": "2026-11-18",
        "transactions": [{"amount": 85, "category": "coffee"}, {"amount": 486.3, "category": "groceries"},
                         {"amount": 38000, "category": "salary", "is_income": True}],
    }})

    monthly_budget: float = Field(..., gt=0, le=10_000_000, description="Бюджет на місяць, ₴")
    transactions: List[BudgetTransaction] = Field(default_factory=list, max_length=500)
    today: Optional[date] = Field(None, description="Дата клієнта; за замовчуванням — дата сервера")
    locale: Literal["uk", "en"] = "uk"


class CategorySpend(BaseModel):
    category: str
    label: str
    amount: float
    share: float = Field(..., ge=0, le=1, description="Частка від усіх витрат")


class BudgetSummaryResponse(BaseModel):
    monthly_budget: float
    spent: float
    income: float
    remaining: float = Field(..., description="Може бути від'ємним, якщо бюджет перевищено")
    percent_used: float = Field(..., description="Відсоток використаного бюджету, може бути > 100")
    status: Literal["ok", "warning", "exceeded"]
    days_left: int = Field(..., ge=1, description="Днів до кінця місяця, включно з сьогодні")
    daily_allowance: float = Field(..., ge=0, description="Скільки можна витрачати на день до кінця місяця")
    by_category: List[CategorySpend]
    message: str
    counted_transactions: int
