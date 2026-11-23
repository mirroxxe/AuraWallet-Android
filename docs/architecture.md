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
│                                            │                         │                                            │
│ HistoryScreen / HistoryDetailsScreen (ЛР4) │                         │                                            │
│ HistoryViewModel → HistoryRepository       │                         │                                            │
│   ↓ Flow                                   │                         │                                            │
│ Room: AuraDatabase (aura.db) → HistoryDao  │                         │                                            │
└────────────────────────────────────────────┘                         └────────────────────────────────────────────┘
```

## Mobile (`mobile/app/src/main/java/com/aurawallet/app/`)
| Файл | Відповідальність |
|---|---|
| `MainActivity.kt` | точка входу; простий стан екрана main → history → details (`rememberSaveable` + `BackHandler`) |
| `AuraApplication.kt` | контейнер залежностей: `AuraDatabase`, `HistoryRepository`, `TransactionRepository` (ЛР4) |
| `ui/HistoryScreen.kt` | ЛР4: список історії (завантаження, порожній стан, статуси), підтвердження видалення/очищення, екран деталей |
| `ui/HistoryViewModel.kt` | ЛР4: Flow записів, видалення, очищення, ручне «Надіслати» для «очікує» |
| `data/local/HistoryEntity.kt`, `HistoryDao.kt`, `AuraDatabase.kt` | ЛР4: таблиця `history`, запити Room, база `aura.db` |
| `data/HistoryRepository.kt` | ЛР4: доменна `HistoryRecord`, мапінг Entity ↔ домен, операції історії окремо від UI |
| `ui/CategorizeScreen.kt` | весь UI: заголовок зі статусом сервера, поле вводу, приклади, картки Loading/Result/Error, список збережених |
| `ui/CategorizeViewModel.kt` | стан екрана (`ResultState`: Idle/Loading/Success/Error), валідація, повтор; успіх → запис в історію, «Зберегти» → підтвердження запису |
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
5. Успіх (HTTP 200 + розібраний JSON) → `history.addDone()` пише запис `DONE` у Room; помилки не пишуться.
6. «Зберегти» → `history.confirm(id, обрана категорія)`; список «Збережені» і бюджет читаються з Room (`observeConfirmed()`).

## Відмінності від архітектури ЛР1
- Додано власний backend з AI (у ЛР1 були лише Monobank і Firebase).
- Немає окремого доменного шару (use cases) і Hilt: ViewModel → Repository напряму.
- Room з'явився в ЛР4 лише для історії запитів (без синхронізації з сервером).

## Локальні дані (ЛР4)
Таблиця `history` (Room, файл `aura.db` у приватній пам'яті застосунку):

| Поле | Тип | Призначення |
|---|---|---|
| `id` | Long, autoincrement | ключ |
| `created_at` | Long (мс) | час результату; сортування DESC |
| `input_text` | String | текст запиту |
| `status` | `DONE` / `PENDING` / `SENDING` | готово / очікує / надсилається |
| `category`, `label`, `confidence`, `low_confidence` | nullable | результат AI |
| `is_income`, `amount`, `currency`, `merchant` | | розібрана операція |
| `explanation`, `alternatives` (JSON), `processing_ms` | | деталі результату |
| `confirmed`, `chosen_category`, `chosen_label` | | користувач натиснув «Зберегти» (і, можливо, змінив категорію) |

Не зберігаються: помилки backend і їхні тексти, HTTP-коди, stack trace, `X-API-Key`, адреса сервера, відповіді, які не вдалося розібрати.

Потік «очікує» (підвищений рівень): помилка мережі/тайм-аут → «Зберегти запит і надіслати пізніше» → `PENDING` → в історії «Надіслати» → атомарний `UPDATE … WHERE status='PENDING'` у `SENDING` (друге натискання повертає 0 рядків і ігнорується) → успіх `DONE` / помилка знову `PENDING`. На старті екрана історії зависле `SENDING` повертається в `PENDING`.
