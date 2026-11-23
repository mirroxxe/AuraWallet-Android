# Architecture

```
┌──────────────────────────── Android (mobile/) ─────────────────────────────┐      HTTP/JSON      ┌──── Backend (backend/) ────┐
│ ui/ (presentation)   CategorizeScreen ── CategorizeViewModel ── UiState    │                     │ main.py   FastAPI          │
│                      HistoryScreen ──── HistoryViewModel                   │                     │ schemas.py Pydantic        │
│        │ use case + інтерфейси (constructor injection)                     │                     │ service.py бізнес-логіка   │
│ domain/  usecase/  CategorizeTransactionUseCase, InputValidator            │                     │ ai/  TF-IDF + LogReg, regex│
│          repository/ CategorizationRepository, BudgetRepository,           │                     │                            │
│                      HistoryStore, DataSourceSwitch  (interfaces)          │                     │                            │
│          model/  Categorization, AppError, CategorizationInput, …          │                     │                            │
│        ▲ реалізують                                                         │                     │                            │
│ data/    repository/ Remote*Repository ─┐  Fake*Repository  Switchable*   │                     │                            │
│                      HistoryRepository  │                                  │                     │                            │
│          mapper/  DTO ↔ domain          │                                  │                     │                            │
│          remote/  AuraApi, Network, DTO, safeApiCall ──────────────────────┼───────────────────► │                            │
│          local/   Room: AuraDatabase, HistoryDao, HistoryEntity            │                     │                            │
│ AuraApplication — composition root (створює реалізації, передає інтерфейси)│                     │                            │
└────────────────────────────────────────────────────────────────────────────┘                     └────────────────────────────┘
```

Графічна схема: [`architecture-final.svg`](architecture-final.svg).

## Mobile (`mobile/app/src/main/java/com/aurawallet/app/`) — після ЛР5
Залежності йдуть лише вниз: `ui → domain ← data`. `domain` не імпортує Android, Retrofit, Room чи Compose.

| Шар / файл | Відповідальність |
|---|---|
| `MainActivity.kt` | точка входу; стан екрана main → history → details (`rememberSaveable` + `BackHandler`) |
| `AuraApplication.kt` | **composition root**: створює `RemoteCategorizationRepository`, `FakeCategorizationRepository`, `SwitchableCategorizationRepository`, `RemoteBudgetRepository`, `HistoryRepository`, `CategorizeTransactionUseCase`; ViewModel отримують їх через `viewModelFactory` як інтерфейси |
| `ui/CategorizeScreen.kt` | UI головного екрана; лише відображає стан і передає дії у ViewModel; debug-картка «Демо-режим» |
| `ui/CategorizeViewModel.kt` | UI-стан (`ResultState`: Idle / Loading / Success / Error, окрема дія `retry()`), статус сервера, бюджет, черга офлайн-запитів; залежить від `CategorizeTransactionUseCase` та інтерфейсів domain |
| `ui/HistoryScreen.kt`, `ui/HistoryViewModel.kt` | історія (ЛР4); надсилання «очікує» через `CategorizationRepository` + `InputValidator` |
| `domain/model/` | `Categorization`, `CategorizationInput`, `SavedTransaction`, `BudgetSummary`; `AppError` (Validation / Network / Timeout / BadResponse / Server / Unknown), `AppErrorException`, `toAppError()` |
| `domain/repository/Repositories.kt` | інтерфейси `CategorizationRepository`, `BudgetRepository`, `HistoryStore`, `DataSourceSwitch` |
| `domain/usecase/InputValidator.kt` | правила введення (порожньо, < 2 символів, > 200, без літер) |
| `domain/usecase/CategorizeTransactionUseCase.kt` | сценарій: валідація → репозиторій → запис успіху в історію; збій запису в історію не ховає результат |
| `data/remote/` | `AuraApi` (Retrofit), `Network` (OkHttp, тайм-аути, `X-API-Key`), DTO, `safeApiCall` — технічні винятки → `AppError` |
| `data/mapper/Mappers.kt` | DTO ↔ domain (`toDomain()`, `toDto()`, `toBudgetItems()`) |
| `data/repository/RemoteRepositories.kt` | `RemoteCategorizationRepository`, `RemoteBudgetRepository` (реальні) |
| `data/repository/FakeCategorizationRepository.kt` | друга реалізація: ключові слова, без мережі; «помилка» імітує збій |
| `data/repository/SwitchableCategorizationRepository.kt` | debug-перемикач real ↔ fake (ЛР5, контрольне розширення) |
| `data/repository/HistoryRepository.kt`, `data/local/*` | Room-історія (ЛР4), реалізує `HistoryStore` |
| `ui/theme/Theme.kt` | токени кольору Aura (light/dark), шрифт Geist |

Конфігурація: `BuildConfig.API_BASE_URL`, `BuildConfig.API_KEY` з `local.properties` (`aura.apiBaseUrl`, `aura.apiKey`) — читаються лише в `data/remote/AuraApi.kt`.

### Обробка помилок
| Технічна причина | Де перетворюється | `AppError` | Що бачить користувач |
|---|---|---|---|
| порожній / некоректний текст | `InputValidator` | `Validation` | текст під полем, запит не йде |
| `SocketTimeoutException` | `safeApiCall` | `Timeout` | «Сервер не відповів вчасно» + Повторити + «Зберегти запит…» |
| інший `IOException` | `safeApiCall` | `Network` | «Немає з'єднання…» + Повторити + «Зберегти запит…» |
| HTTP з `{"error":…}` | `safeApiCall` | `Server(message, code)` | повідомлення backend |
| битий JSON | `safeApiCall` | `BadResponse` | «Незрозуміла відповідь» |
| будь-що інше | `safeApiCall` / `toAppError()` | `Unknown` | «Щось пішло не так» |

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
2. `CategorizeTransactionUseCase(text)` → `CategorizationRepository.categorize(CategorizationInput)` → (`Remote…`) `POST {text, locale}`.
3. Backend: Pydantic-валідація → `parse` → `classifier.predict(cleaned_text)` → якщо `confidence < AI_MIN_CONFIDENCE` → `other` + `low_confidence`.
4. JSON → `CategorizeResponseDto.toDomain()` → `ResultState.Success` → картка результату.
5. Успіх (HTTP 200 + розібраний JSON) → `history.addDone()` пише запис `DONE` у Room; помилки не пишуться.
6. «Зберегти» → `history.confirm(id, обрана категорія)`; список «Збережені» і бюджет читаються з Room (`observeConfirmed()`).

## Відмінності від архітектури ЛР1
- Додано власний backend з AI (у ЛР1 були лише Monobank і Firebase).
- Доменний шар (use case + інтерфейси) з'явився в ЛР5 для сценарію розпізнавання; DI — ручний (composition root), без Hilt.
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

## Потік даних «Персоналізація» (ЛР6)
1. Room: підтверджені записи, де `chosen_category ≠ category` → `HistoryDao.corrections(50)` → `HistoryStore.personalExamples()`.
2. `CategorizeTransactionUseCase(text, personalize)` додає їх у `CategorizationInput.personalExamples` (чип «Враховувати мої виправлення» в UI).
3. Mapper → `CategorizeRequestDto.personal_examples` → `POST /api/v1/transactions/categorize`.
4. Backend `service.categorize`: `parse` → `personalizer.prepare` → `classifier.predict` + `personalizer.best_match` → `personalizer.decide` → відповідь з `confidence_source` і `personalization`.
5. Mapper → `Categorization.personalization` → `ResultCard`: жовта плашка «Персоналізовано: як «…»», підпис «Схожість з вашою операцією».
Виправлення зберігаються лише на пристрої; сервер їх не зберігає й не логує.

