"""HTTP-тести API. Потребують fastapi і httpx (pip install -r requirements-dev.txt)."""
import pytest

pytest.importorskip("fastapi")
pytest.importorskip("httpx")
from fastapi.testclient import TestClient  # noqa: E402

from app.main import app  # noqa: E402


@pytest.fixture(scope="module")
def client():
    with TestClient(app) as c:  # with → запускає lifespan і навчає модель
        yield c


def test_health(client):
    r = client.get("/health")
    assert r.status_code == 200 and r.json()["status"] == "ok" and r.json()["model_loaded"]


def test_categorize_ok(client):
    r = client.post("/api/v1/transactions/categorize", json={"text": "Кава в Aroma Kava 85 грн"})
    body = r.json()
    assert r.status_code == 200
    assert body["category"] == "coffee" and body["amount"] == 85 and body["currency"] == "UAH"


@pytest.mark.parametrize("payload,field", [({"text": ""}, "text"), ({}, "text"), ({"text": "123"}, "text"),
                                           ({"text": "кава", "locale": "de"}, "locale")])
def test_categorize_validation(client, payload, field):
    r = client.post("/api/v1/transactions/categorize", json=payload)
    assert r.status_code == 422
    assert r.json()["error"]["code"] == "validation_error" and r.json()["error"]["field"] == field


def test_invalid_json(client):
    r = client.post("/api/v1/transactions/categorize", content="{not json", headers={"Content-Type": "application/json"})
    assert r.status_code == 422 and r.json()["error"]["code"] == "validation_error"


def test_ai_failure_is_controlled(client, monkeypatch):
    from app import main
    monkeypatch.setitem(main.state, "classifier", None)
    r = client.post("/api/v1/transactions/categorize", json={"text": "кава"})
    assert r.status_code == 503
    assert r.json() == {"error": {"code": "ai_unavailable",
                                  "message": "AI-сервіс тимчасово недоступний. Спробуйте ще раз або оберіть категорію вручну."}}


def test_budget_ok(client):
    r = client.post("/api/v1/budget/summary", json={"monthly_budget": 100, "today": "2026-11-18",
                                                     "transactions": [{"amount": 85, "category": "coffee"}]})
    assert r.status_code == 200
    body = r.json()
    assert body["status"] == "warning" and body["spent"] == 85 and body["days_left"] == 13


@pytest.mark.parametrize("payload,field", [
    ({"monthly_budget": 0, "transactions": []}, "monthly_budget"),
    ({"monthly_budget": 100, "transactions": [{"amount": 5, "category": "nope"}]}, "transactions.0.category"),
])
def test_budget_validation(client, payload, field):
    r = client.post("/api/v1/budget/summary", json=payload)
    assert r.status_code == 422
    assert r.json()["error"]["code"] == "validation_error" and r.json()["error"]["field"] == field


def test_categorize_still_works_after_lab3(client):
    """Регресія ЛР2: основний сценарій не зламаний."""
    r = client.post("/api/v1/transactions/categorize", json={"text": "Кава в Aroma Kava 85 грн"})
    assert r.status_code == 200 and r.json()["category"] == "coffee"


# ---------- ЛР6: персоналізація ----------

def test_categorize_with_personal_examples(client):
    r = client.post("/api/v1/transactions/categorize", json={
        "text": "Епіцентр 640 грн",
        "personal_examples": [{"text": "Епіцентр фарба для стін 1200", "category": "housing"}],
    })
    body = r.json()
    assert r.status_code == 200
    assert body["category"] == "housing" and body["confidence_source"] == "personal"
    assert body["personalization"]["model_category"] == "shopping"


def test_categorize_without_examples_has_model_source(client):
    body = client.post("/api/v1/transactions/categorize", json={"text": "Кава 85"}).json()
    assert body["confidence_source"] == "model" and body["personalization"] is None


def test_personal_example_validation(client):
    r = client.post("/api/v1/transactions/categorize", json={
        "text": "Епіцентр 640", "personal_examples": [{"text": "Епіцентр", "category": "nope"}]})
    assert r.status_code == 422
    assert r.json()["error"]["field"] == "personal_examples.0.category"

