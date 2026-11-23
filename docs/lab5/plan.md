# ЛР5 — Оптимізація архітектури (варіант А: Mobile)

**Сценарій:** «AI-розпізнавання операції» (ЛР2): текст → валідація → `POST /api/v1/transactions/categorize` → результат / помилка → запис в історію (ЛР4).

## Проблеми початкової реалізації (після ЛР4)
| # | Проблема | Де в коді |
|---|---|---|
| P1 | ViewModel змішує відповідальності: валідацію введення, виклик API, запис в історію, визначення статусу сервера, бюджет і UI-стан | `ui/CategorizeViewModel.kt` (`submit()`, `validate()`, `refreshBudget()`) |
| P2 | Жорстка залежність від конкретного класу й глобального Retrofit: `TransactionRepository(api = Network.api)`, інтерфейсу немає; тест ViewModel без запущеного backend неможливий | `data/TransactionRepository.kt`, `data/AuraApi.kt` (`object Network`) |
| P3 | Доменні моделі лежать разом із перетворенням DTO: `Models.kt` імпортує `CategorizeResponseDto`, `toBudgetItems()` повертає DTO — домен залежить від формату JSON | `data/Models.kt` |
| P4 | Будь-який невідомий виняток стає `AppError.Network` («Немає з'єднання»), навіть якщо мережа ні до чого | `data/TransactionRepository.kt::toAppError()` |
| P5 | Сценарій дубльовано: `HistoryViewModel.send()` сам викликає `TransactionRepository.categorize()` без спільної валідації | `ui/HistoryViewModel.kt` |

## Цільова структура
```
ui/                         presentation: Screen, ViewModel, UiState (Idle/Loading/Success/Error)
domain/model/               Categorization, AppError, CategorizationInput, SavedTransaction, BudgetSummary
domain/repository/          CategorizationRepository, BudgetRepository, HistoryStore, DataSourceSwitch (інтерфейси)
domain/usecase/             InputValidator, CategorizeTransactionUseCase
data/remote/                AuraApi, Network, DTO, safeApiCall (технічні винятки → AppError)
data/mapper/                DTO ↔ domain
data/repository/            RemoteCategorizationRepository, FakeCategorizationRepository,
                            SwitchableCategorizationRepository, RemoteBudgetRepository, HistoryRepository
data/local/                 Room (без змін)
AuraApplication             composition root: створює реалізації й передає їх через конструктори
```
Інтерфейс `CategorizationRepository` має дві реалізації: **Remote** (FastAPI) і **Fake** (правила за ключовими словами, без мережі).

## Кроки (кожен — окремий commit)
1. План і вибір варіанта (цей файл, README).
2. Regression-тести поточної поведінки: тексти валідації, мапінг DTO, перетворення технічних помилок у `AppError` (через fake `AuraApi`).
3. Domain: моделі, `AppError` (+ `Validation`, `Unknown`), інтерфейси, `InputValidator`, `CategorizeTransactionUseCase`.
4. Data: розділення на remote / mapper / repository, `FakeCategorizationRepository`; DI через `AuraApplication` і `viewModelFactory`; ViewModel працюють лише з інтерфейсами й use case.
5. Тести без мережі: use case з fake repository, ViewModel з `kotlinx-coroutines-test`.
6. Контрольне розширення: debug-перемикач «Сервер / Демо» (real ↔ fake repository) + тест.
7. Документація: architecture, decisions, testing, known-limitations, README.

## Критерії готовності
- M1–M6, B1–B6, H1–H10 працюють як раніше; тексти помилок не змінились (крім P4: невідома помилка → «Щось пішло не так»).
- ViewModel і use case не імпортують `data.remote` / Retrofit.
- ≥ 5 нових JVM-тестів проходять без backend.

## Нова залежність
`org.jetbrains.kotlinx:kotlinx-coroutines-test` (лише `testImplementation`): `Dispatchers.setMain` і `runTest` для тесту ViewModel без емулятора. Альтернатива — ручна підміна диспетчера через параметр конструктора, але це засмічує продакшн-код.
