# Architecture

```
┌──────────── Android (mobile/) ─────────────┐        HTTP/JSON        ┌──────────── Backend (backend/) ────────────┐
│ CategorizeScreen (Compose)                 │                         │ main.py      FastAPI, обробники помилок    │
│   ↑ StateFlow<CategorizeUiState>           │  POST /api/v1/          │ schemas.py   Pydantic-моделі               │
│ CategorizeViewModel                        │  transactions/categorize│ service.py   бізнес-логіка                 │
│   ↓ suspend                                │ ──────────────────────► │ ai/parser.py сума, валюта, магазин (regex) │
│ TransactionRepository (мапінг помилок)     │                         │ ai/classifier.py TF-IDF + LogisticRegression│
│   ↓                                        │ ◄────────────────────── │ config.py    змінні середовища             │
│ AuraApi (Retrofit + OkHttp + kotlinx.ser.) │   CategorizeResponse    │                                            │
└────────────────────────────────────────────┘                         └────────────────────────────────────────────┘
```

## Mobile (`mobile/app/src/main/java/com/aurawallet/app/`)
| Файл | Відповідальність |
|---|---|
| `MainActivity.kt` | точка входу, `AuraTheme { CategorizeScreen() }` |
| `ui/CategorizeScreen.kt` | весь UI: заголовок зі статусом сервера, поле вводу, приклади, картки Loading/Result/Error, список збережених |
| `ui/CategorizeViewModel.kt` | стан екрана (`ResultState`: Idle/Loading/Success/Error), валідація, повтор, збереження в пам'яті |
| `data/AuraApi.kt` | Retrofit-інтерфейс, `Network` (OkHttp, тайм-аути 5/10 с, `X-API-Key`), `Json { ignoreUnknownKeys }` |
| `data/ApiModels.kt` | DTO, що дзеркалять Pydantic-моделі |
| `data/Models.kt` | доменні моделі (`Categorization`, `SavedTransaction`, `AppError`) |
| `data/TransactionRepository.kt` | виклики API → `Result<T>`; IOException/HttpException/SerializationException → `AppError` |
| `ui/theme/Theme.kt` | токени кольору Aura (light/dark), шрифт Geist |

Конфігурація: `BuildConfig.API_BASE_URL`, `BuildConfig.API_KEY` з `local.properties` (`aura.apiBaseUrl`, `aura.apiKey`).

## Backend (`backend/app/`)
| Файл | Відповідальність |
|---|---|
| `main.py` | `GET /health`, `POST /api/v1/transactions/categorize`, єдиний формат помилок, lifespan навчає модель |
| `schemas.py` | `CategorizeRequest/Response`, `HealthResponse`, `ErrorResponse` |
| `service.py` | `categorize()` = parser + classifier + поріг впевненості + пояснення; `budget_summary()` (ЛР3) |
| `ai/parser.py` | `parse(text)` → сума, валюта, магазин, текст без суми |
| `ai/classifier.py` | `TransactionClassifier` (навчання при старті на `training_data.csv`) |
| `categories.py` | коди й підписи категорій uk/en, `INCOME_CATEGORIES` |
| `config.py` | `Settings` зі змінних середовища |

## Потік даних «Бюджет місяця» (ЛР3)
1. «Зберегти» або «Застосувати» ліміт → `vm.refreshBudget()` (якщо збережених операцій немає → підказка без запиту).
2. `repository.budgetSummary(budget, saved)` → `saved.toBudgetItems()` (лише UAH із сумою) → `POST /api/v1/budget/summary`.
3. Backend `service.budget_summary()` → статус, денний ліміт, розподіл.
4. `BudgetState.Ready` → картка «Бюджет місяця»; помилка → `BudgetState.Error` з «Повторити», не впливає на картку розпізнавання.

## Потік даних
1. UI → `vm.submit()` → валідація (порожньо / лише цифри → помилка під полем, запит не йде).
2. `repository.categorize(text)` → `POST {text, locale}`.
3. Backend: Pydantic-валідація → `parse` → `classifier.predict(cleaned_text)` → якщо `confidence < AI_MIN_CONFIDENCE` → `other` + `low_confidence`.
4. JSON → `CategorizeResponseDto.toDomain()` → `ResultState.Success` → картка результату.
5. «Зберегти» → `SavedTransaction` додається на початок списку (лише в пам'яті).

## Відмінності від архітектури ЛР1
- Додано власний backend з AI (у ЛР1 були лише Monobank і Firebase).
- Немає окремого доменного шару (use cases) і Hilt: ViewModel → Repository напряму.
- Немає Room: дані живуть до закриття застосунку.
