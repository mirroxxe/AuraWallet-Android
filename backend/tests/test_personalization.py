"""ЛР6: персоналізація за виправленнями користувача (без HTTP)."""
import csv
from pathlib import Path

import pytest
from pydantic import ValidationError

from app.ai import personalizer
from app.ai.classifier import TransactionClassifier
from app.config import Settings
from app.schemas import CategorizeRequest, PersonalExample
from app.service import categorize

EVAL = Path(__file__).resolve().parents[1] / "eval"


def _csv(name):
    with (EVAL / name).open(encoding="utf-8") as fh:
        return list(csv.DictReader(fh))


HISTORY = [{"text": r["text"], "category": r["category"]} for r in _csv("personal_history.csv")]


@pytest.fixture(scope="module")
def clf():
    return TransactionClassifier()


def run(clf, text, examples=None, settings=None):
    req = CategorizeRequest(text=text, personal_examples=examples or [])
    return categorize(req, clf, settings or Settings())


def test_without_examples_response_is_unchanged(clf):
    r = run(clf, "Кава в Aroma Kava 85 грн")
    assert r.category == "coffee" and r.confidence_source == "model" and r.personalization is None
    assert r.explanation.startswith("Схоже на «Кава»")


def test_correction_overrides_confident_model(clf):
    r = run(clf, "Епіцентр 640 грн", HISTORY)
    assert r.category == "housing" and r.label == "Житло"
    assert r.confidence_source == "personal"
    assert r.personalization.model_category == "shopping"
    assert r.alternatives[0].category == "shopping", "прогноз моделі лишається першою альтернативою"
    assert "Ви раніше віднесли" in r.explanation


def test_correction_resolves_low_confidence(clf):
    base = run(clf, "Книгарня Є 380 грн")
    assert base.low_confidence and base.category == "other"
    r = run(clf, "Книгарня Є 380 грн", HISTORY)
    assert r.category == "entertainment" and not r.low_confidence


def test_amount_is_ignored_when_matching(clf):
    a = run(clf, "пральня 5", HISTORY).personalization.similarity
    b = run(clf, "пральня 99999 грн", HISTORY).personalization.similarity
    assert a == b == 1.0


def test_unrelated_examples_do_not_change_control_examples(clf):
    for row in _csv("control_examples.csv"):
        a, b = run(clf, row["text"]), run(clf, row["text"], HISTORY)
        assert (a.category, a.low_confidence) == (b.category, b.low_confidence), row["text"]


def test_threshold_from_settings_is_respected(clf):
    strict = Settings(personal_min_similarity=0.99)
    r = run(clf, "Епіцентр лампочки 210", HISTORY, strict)
    assert r.category == "shopping" and r.personalization is None


def test_same_category_keeps_model_result(clf):
    r = run(clf, "Glovo суші 700", [{"text": "Glovo суші 520", "category": "cafes"}])
    assert r.category == "cafes" and r.confidence_source == "model" and r.personalization is None


def test_prepare_dedupes_and_drops_amount_only_examples():
    ex = [PersonalExample(text="Епіцентр 100", category="housing"),
          PersonalExample(text="епіцентр 900 грн", category="shopping"),
          PersonalExample(text="на 500", category="gifts")]
    prepared = personalizer.prepare(ex)
    assert [p.category for p in prepared] == ["housing"], "дубль «епіцентр» і «на» відкинуто, перший (новіший) лишився"
    assert prepared[0].cleaned == "епіцентр"


@pytest.mark.parametrize("examples", [
    [{"text": "Епіцентр", "category": "unknown_cat"}],
    [{"text": "", "category": "housing"}],
    [{"text": "Епіцентр", "category": "housing"}] * 51,
])
def test_invalid_examples_are_rejected(examples):
    with pytest.raises(ValidationError):
        CategorizeRequest(text="Епіцентр 100", personal_examples=examples)


def test_acceptance_criteria_k1_k2(clf):
    """K1: точність на персональних запитах з контекстом ≥ 80 %. K2 перевірено тестом вище."""
    rows = _csv("personal_queries.csv")
    hits = sum(run(clf, r["text"], HISTORY).category == r["expected"] for r in rows)
    assert hits / len(rows) >= 0.80, f"{hits}/{len(rows)}"
