"""AuraWallet API — FastAPI-сервіс для розпізнавання категорії витрати з тексту.

Запуск:  uvicorn app.main:app --host 0.0.0.0 --port 8000
Swagger: http://localhost:8000/docs
"""
from __future__ import annotations

import logging
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, Header, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from .ai.classifier import AIUnavailableError, TransactionClassifier
from .config import Settings, get_settings
from .schemas import CategorizeRequest, CategorizeResponse, ErrorResponse, HealthResponse
from .service import categorize

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("aurawallet")

state: dict[str, TransactionClassifier | None] = {"classifier": None}


@asynccontextmanager
async def lifespan(_: FastAPI):
    try:
        clf = TransactionClassifier()
        state["classifier"] = clf
        log.info("Classifier trained on %d samples, %d classes in %.0f ms", clf.samples, len(clf.classes), clf.train_ms)
    except Exception:
        log.exception("Classifier failed to load")
    yield


settings = get_settings()
app = FastAPI(
    title=settings.app_name,
    version=settings.version,
    description="Backend для AuraWallet: AI-розпізнавання категорії, суми й магазину з опису витрати.",
    lifespan=lifespan,
)


def _error(status: int, code: str, message: str, field: str | None = None) -> JSONResponse:
    body = {"error": {"code": code, "message": message}}
    if field:
        body["error"]["field"] = field
    return JSONResponse(status_code=status, content=body)


@app.exception_handler(RequestValidationError)
async def on_validation_error(_: Request, exc: RequestValidationError) -> JSONResponse:
    first = exc.errors()[0] if exc.errors() else {}
    loc = [str(p) for p in first.get("loc", []) if p != "body"]
    field = loc[0] if loc else None
    kind = first.get("type", "")
    if kind == "missing":
        message = f"Поле «{field}» обов'язкове"
    elif kind == "string_too_short":
        message = "Опис не може бути порожнім"
    elif kind == "string_too_long":
        message = f"Опис занадто довгий: максимум {settings.max_text_length} символів"
    elif kind in {"json_invalid", "model_attributes_type"}:
        message = "Тіло запиту має бути коректним JSON-об'єктом"
    else:
        message = str(first.get("msg", "Некоректні дані")).removeprefix("Value error, ")
    return _error(422, "validation_error", message, field)


@app.exception_handler(AIUnavailableError)
async def on_ai_error(_: Request, exc: AIUnavailableError) -> JSONResponse:
    log.warning("AI unavailable: %s", exc)
    return _error(503, "ai_unavailable", "AI-сервіс тимчасово недоступний. Спробуйте ще раз або оберіть категорію вручну.")


@app.exception_handler(Exception)
async def on_unexpected_error(_: Request, exc: Exception) -> JSONResponse:
    log.exception("Unhandled error")  # деталі лише в лог сервера, не клієнту
    return _error(500, "internal_error", "Сталася внутрішня помилка сервера. Спробуйте пізніше.")


def require_api_key(x_api_key: str | None = Header(default=None)) -> None:
    if settings.api_key and x_api_key != settings.api_key:
        raise PermissionError


@app.exception_handler(PermissionError)
async def on_permission_error(_: Request, __: PermissionError) -> JSONResponse:
    return _error(401, "unauthorized", "Неправильний або відсутній ключ доступу X-API-Key")


@app.get("/health", response_model=HealthResponse, tags=["service"])
def health() -> HealthResponse:
    clf = state["classifier"]
    return HealthResponse(
        status="ok" if clf else "degraded",
        version=settings.version,
        model_loaded=clf is not None,
        model_classes=len(clf.classes) if clf else 0,
        training_samples=clf.samples if clf else 0,
    )


@app.post(
    "/api/v1/transactions/categorize",
    response_model=CategorizeResponse,
    tags=["transactions"],
    summary="Розпізнати категорію, суму й магазин з опису операції",
    responses={401: {"model": ErrorResponse}, 422: {"model": ErrorResponse}, 503: {"model": ErrorResponse}},
)
def categorize_transaction(req: CategorizeRequest, cfg: Settings = Depends(get_settings),
                           _: None = Depends(require_api_key)) -> CategorizeResponse:
    if len(req.text) > cfg.max_text_length:
        raise RequestValidationError([{"type": "string_too_long", "loc": ("body", "text"), "msg": "too long"}])
    return categorize(req, state["classifier"], cfg)
