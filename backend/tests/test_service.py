"""Тести бізнес-логіки й AI-компонента (без HTTP)."""
import pytest

from app.ai.classifier import AIUnavailableError, TransactionClassifier
from app.ai.parser import parse
from app.config import Settings
from app.schemas import CategorizeRequest
from app.service import categorize


@pytest.fixture(scope="module")
def clf():
    return TransactionClassifier()


@pytest.mark.parametrize("text,amount,currency,merchant", [
    ("Кава в Aroma Kava 85 грн", 85.0, "UAH", "Aroma Kava"),
    ("Сільпо 486,30", 486.30, "UAH", "Сільпо"),
    ("зарплата 38 000 грн", 38000.0, "UAH", None),
    ("Rozetka навушники $45", 45.0, "USD", "Rozetka"),
    ("квитки 20 євро", 20.0, "EUR", None),
    ("просто текст", None, None, None),
])
def test_parser(text, amount, currency, merchant):
    p = parse(text)
    assert p.amount == amount and p.currency == currency and p.merchant == merchant


@pytest.mark.parametrize("text,category", [
    ("Кава в Aroma Kava 85 грн", "coffee"),
    ("Uklon до роботи 142", "transport"),
    ("Netflix 299 грн", "subscriptions"),
    ("Сільпо 486,30", "groceries"),
    ("зарплата за листопад 38 000 грн", "salary"),
    ("аптека ліки 230", "health"),
])
def test_categories(clf, text, category):
    r = categorize(CategorizeRequest(text=text), clf, Settings())
    assert r.category == category
    assert 0 <= r.confidence <= 1 and r.explanation


def test_low_confidence_goes_to_other(clf):
    r = categorize(CategorizeRequest(text="щось незрозуміле xyz"), clf, Settings())
    assert r.category == "other" and r.low_confidence and r.alternatives


def test_income_flag(clf):
    assert categorize(CategorizeRequest(text="зарплата"), clf, Settings()).is_income


def test_simulated_ai_failure(clf):
    with pytest.raises(AIUnavailableError):
        categorize(CategorizeRequest(text="кава"), clf, Settings(simulate_ai_failure=True))


@pytest.mark.parametrize("bad", ["", " ", "1", "12345"])
def test_request_validation(bad):
    with pytest.raises(ValueError):
        CategorizeRequest(text=bad)
