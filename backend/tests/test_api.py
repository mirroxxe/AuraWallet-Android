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
