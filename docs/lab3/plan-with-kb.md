# План з Knowledge Base

**Як отримано:** той самий текст задачі + інструкція «прочитай `docs/` і `docs/ai-instructions.md`, перевір `git ls-files`, спершу поясни поточну реалізацію».

**Prompt:**
> Прочитай docs/ (architecture.md, api-contract.md, decisions.md, known-limitations.md, ai-instructions.md) і структуру репозиторію. Поясни, як зараз ідуть дані від екрана до AI. Потім склади мінімальний план функції з task.md: кроки, критерії готовності, список файлів. Дотримуйся ai-instructions.md.

**Відповідь AI — розуміння поточного стану:**
`CategorizeScreen` → `CategorizeViewModel` (StateFlow, `ResultState`) → `TransactionRepository` → Retrofit `AuraApi` → `POST /api/v1/transactions/categorize` → `service.categorize` (parser + classifier). Збережені операції — `List<SavedTransaction>` у стані ViewModel, лише в пам'яті. Помилки backend мають формат `{"error":{code,message,field}}`, mobile показує `message`. Room, Hilt, навігації й автентифікації немає, це свідомі рішення D8 і межі MVP.

**План (мінімальні кроки):**
1. **Backend: контракт і логіка.** `schemas.py`: `BudgetTransaction`, `BudgetSummaryRequest`, `BudgetSummaryResponse`. `service.py`: `budget_summary()` рахує витрачене (без доходів), залишок, % використання, статус `ok` / `warning` (≥80%) / `exceeded` (≥100%), днів до кінця місяця, денний ліміт, розподіл за категоріями й повідомлення. `main.py`: `POST /api/v1/budget/summary`, помилки в наявному форматі. Нових залежностей немає.
2. **Backend: тести.** Пороги 79.9/80/100%, доходи не рахуються, невідома категорія → 422, порожній список → 0 витрачено, останній день місяця → `days_left = 1`, HTTP 200/422.
3. **Mobile: дані.** `ApiModels.kt`: DTO бюджету. `AuraApi.kt`: новий метод. `Models.kt`: `BudgetSummary`. `TransactionRepository.kt`: `budgetSummary()` з тим самим мапінгом помилок.
4. **Mobile: стан і UI.** `CategorizeViewModel`: `monthlyBudget` (за замовчуванням 18 000 ₴, як в iOS) і `budget: BudgetState` (Idle/Loading/Ready/Error); перерахунок після кожного «Зберегти» і зміни бюджету. Збої бюджету не ламають сценарій ЛР2. `CategorizeScreen`: картка «Бюджет місяця» з прогресом, статусом, денним лімітом, стековою смугою категорій (як на головному екрані у Figma) і полем бюджету. Unit-тест валідації бюджету.
5. **Документація.** `api-contract.md`, `architecture.md`, `requirements.md` (ФВ-3 → ✅), `testing.md` (сценарії B1–B4), `decisions.md` (D13), README.

**Не робимо:** Room, Hilt, навігацію, push, ліміти за категоріями (прибрані власником у iOS-версії, рішення iOS), автентифікацію.

**Файли:** `backend/app/schemas.py`, `backend/app/service.py`, `backend/app/main.py`, `backend/tests/test_budget.py`, `mobile/.../data/ApiModels.kt`, `AuraApi.kt`, `Models.kt`, `TransactionRepository.kt`, `ui/CategorizeViewModel.kt`, `ui/CategorizeScreen.kt`, `mobile/app/src/test/.../BudgetTest.kt`, `docs/*.md`, `README.md`.

**Критерії готовності:** `pytest -q` зелений; `./gradlew test` зелений; сценарій ЛР2 (M1–M6) працює; бюджет 1 000 ₴ + кава 85 ₴ → 8,5%, `ok`; бюджет 100 ₴ + 85 ₴ → `warning`; бюджет 80 ₴ → `exceeded`; вимкнений backend → у картці бюджету помилка з повтором, а розпізнавання далі показує свою помилку.
