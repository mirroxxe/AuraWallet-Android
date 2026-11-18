"""Pydantic-моделі запиту й відповіді API."""
from __future__ import annotations

from typing import Literal

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
    amount: float | None = Field(None, description="Сума, якщо знайдена в тексті")
    currency: str | None = Field(None, description="ISO-код валюти: UAH, USD, EUR, PLN")
    merchant: str | None = Field(None, description="Відомий магазин або сервіс, якщо знайдено")
    explanation: str
    alternatives: list[Alternative] = []
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
    field: str | None = None


class ErrorResponse(BaseModel):
    error: ErrorBody
