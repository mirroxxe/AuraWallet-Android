"""Налаштування сервісу. Усі значення беруться зі змінних середовища (див. .env.example)."""
from __future__ import annotations

import os
from dataclasses import dataclass, field


def _bool(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}


def _float(name: str, default: float) -> float:
    try:
        return float(os.getenv(name, default))
    except ValueError:
        return default


@dataclass(frozen=True)
class Settings:
    app_name: str = "AuraWallet API"
    version: str = "0.3.0"
    # Мінімальна впевненість моделі; нижче неї категорія повертається як "other".
    min_confidence: float = field(default_factory=lambda: _float("AI_MIN_CONFIDENCE", 0.30))
    # Для тесту сценарію «AI повернув помилку»: якщо true, AI-компонент завжди падає.
    simulate_ai_failure: bool = field(default_factory=lambda: _bool("AI_SIMULATE_FAILURE", False))
    # Необов'язковий ключ доступу. Порожній — перевірка вимкнена.
    api_key: str = field(default_factory=lambda: os.getenv("API_KEY", ""))
    # Максимальна довжина опису операції.
    max_text_length: int = field(default_factory=lambda: int(_float("MAX_TEXT_LENGTH", 200)))


def get_settings() -> Settings:
    return Settings()
