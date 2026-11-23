# ЛР6 · Етап 3 — план реалізації

**Де виконується AI:** backend (FastAPI), як і раніше. Персональні приклади зберігаються лише на пристрої (Room) і передаються з кожним запитом; сервер їх не зберігає.

## Потік даних
```
Room history (confirmed, chosen_category ≠ category)  ── HistoryStore.personalExamples(50)
        │
CategorizeTransactionUseCase(text, personalize)  ── CategorizationInput(text, locale, personalExamples)
        │  mapper → CategorizeRequestDto(text, locale, personal_examples)
POST /api/v1/transactions/categorize
        │
service.categorize:
  1. підготовка     parse(text) → cleaned_text;  personalizer.prepare(examples) → нормалізовані приклади
  2. AI-обробка     classifier.predict(cleaned_text);  personalizer.best_match(classifier.embed(...))
  3. результат      personalizer.decide(prediction, match) → категорія, confidence_source, пояснення
        │
CategorizeResponse(+ confidence_source, personalization) → mapper → Categorization(personalization)
        │
ResultCard: «Персоналізовано: як «…»», схожість замість впевненості; чип «Мої виправлення» вмикає/вимикає
```

## Компоненти, що змінюються
| Шар | Файли |
|---|---|
| API-контракт | `docs/api-contract.md`, `backend/app/schemas.py`, `mobile/.../data/remote/ApiModels.kt` (одним кроком) |
| Backend | `app/ai/personalizer.py` (новий), `app/ai/classifier.py` (`embed()`), `app/service.py`, `app/config.py` (`AI_PERSONAL_MIN_SIMILARITY`), тести |
| Mobile data | `HistoryDao` (запит виправлень), `HistoryRepository`, `mapper/Mappers.kt`, `FakeCategorizationRepository` |
| Mobile domain | `CategorizationInput.personalExamples`, `Categorization.personalization`, `HistoryStore.personalExamples()`, use case |
| Mobile UI | `CategorizeViewModel` (`personalize`), `CategorizeScreen` (чип, блок у ResultCard) |
| Оцінка | `backend/eval/evaluate.py` → `docs/lab6/results.md` |

Нових залежностей немає (scikit-learn уже є). Модель не перенавчається: версія та параметри ті самі (`training_data.csv`, 201 приклад, C=12).
