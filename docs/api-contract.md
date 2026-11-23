# API contract

Base URL (dev): `http://127.0.0.1:8000/` через `adb reverse`, або `http://10.0.2.2:8000/` з емулятора. Swagger: `/docs`.
Кодування: JSON UTF-8. Необов'язковий заголовок `X-API-Key` (обов'язковий, лише якщо на сервері задано `API_KEY`).

## `GET /health`
200:
```json
{ "status": "ok", "version": "0.3.0", "model_loaded": true, "model_classes": 17, "training_samples": 201 }
```
`status = "degraded"`, якщо модель не завантажилась.

## `POST /api/v1/transactions/categorize`
Запит `CategorizeRequest`:
| Поле | Тип | Обов'язкове | Обмеження |
|---|---|---|---|
| `text` | string | так | 2–200 символів після нормалізації пробілів, має містити хоча б одну літеру |
| `locale` | `"uk"` \| `"en"` | ні (`uk`) | мова `label` і `explanation` |
| `personal_examples` | `[{text, category}]` | ні (`[]`) | ЛР6: 0–50 виправлень користувача, новіші першими; `text` 2–200 символів, `category` — відомий код. Невідома категорія → 422, `field = personal_examples.N.category` |

Відповідь 200 `CategorizeResponse`:
| Поле | Тип | Опис |
|---|---|---|
| `category` | string | код категорії або `other` |
| `label` | string | назва категорії мовою `locale` |
| `confidence` | number 0..1 | при `confidence_source = model` — ймовірність найкращої категорії від моделі (навіть якщо повернуто `other`); при `personal` — косинусна схожість із виправленням користувача, **не** ймовірність правильності |
| `is_income` | bool | `true` для `salary`, `freelance`, `cashback` |
| `amount` | number \| null | сума з тексту |
| `currency` | `UAH`/`USD`/`EUR`/`PLN` \| null | |
| `merchant` | string \| null | відомий магазин зі словника |
| `explanation` | string | людське пояснення |
| `alternatives` | `[{category,label,confidence}]` | до 3 варіантів |
| `low_confidence` | bool | модель невпевнена → обрати вручну |
| `processing_ms` | number | час обробки на сервері |
| `confidence_source` | `"model"` \| `"personal"` | ЛР6: звідки `confidence` |
| `personalization` | object \| null | ЛР6: `{matched_text, matched_category, similarity, model_category, model_label}`, якщо категорію взято з виправлення користувача |

Приклад (ЛР6):
```json
// запит
{"text": "Епіцентр 640 грн", "personal_examples": [{"text": "Епіцентр фарба для стін 1200", "category": "housing"}]}
// відповідь (скорочено)
{"category": "housing", "label": "Житло", "confidence": 0.87, "confidence_source": "personal",
 "personalization": {"matched_text": "Епіцентр фарба для стін 1200", "matched_category": "housing",
                     "similarity": 0.87, "model_category": "shopping", "model_label": "Покупки"},
 "alternatives": [{"category": "shopping", "label": "Покупки", "confidence": 0.84}], "low_confidence": false, "...": "..."}
```

Mobile ігнорує невідомі поля (`ignoreUnknownKeys = true`), тож додавання нових полів у відповідь — сумісна зміна. Видалення чи перейменування — несумісна, потребує оновлення `ApiModels.kt` і цього документа.

## `POST /api/v1/budget/summary` (ЛР3)
Запит `BudgetSummaryRequest`:
| Поле | Тип | Обов'язкове | Обмеження |
|---|---|---|---|
| `monthly_budget` | number | так | > 0, ≤ 10 000 000 |
| `transactions` | `[{amount, category, is_income?}]` | ні (`[]`) | до 500 елементів; `amount` > 0; `category` — відомий код |
| `today` | `YYYY-MM-DD` | ні | дата клієнта; за замовчуванням дата сервера |
| `locale` | `"uk"` \| `"en"` | ні | |

Відповідь 200 `BudgetSummaryResponse`: `monthly_budget`, `spent` (лише витрати), `income`, `remaining` (може бути < 0), `percent_used`, `status` (`ok` < 80% ≤ `warning` < 100% ≤ `exceeded`, рахується з неокругленого відсотка), `days_left` (включно з сьогодні), `daily_allowance` (≥ 0), `by_category` `[{category,label,amount,share}]` за спаданням, `message`, `counted_transactions`.

Приклад:
```json
{"monthly_budget": 100, "today": "2026-11-18", "transactions": [{"amount": 85, "category": "coffee"}]}
→ {"spent": 85.0, "remaining": 15.0, "percent_used": 85.0, "status": "warning", "days_left": 13,
   "daily_allowance": 1.15, "by_category": [{"category": "coffee", "label": "Кава", "amount": 85.0, "share": 1.0}],
   "message": "Використано 80% бюджету: лишилось ₴15 на 13 дн. — приблизно ₴1 на день.", ...}
```
Помилки: 422 `validation_error` з `field` у вигляді шляху, напр. `transactions.0.category`.

## Помилки
Завжди `{"error": {"code": string, "message": string, "field"?: string}}`, без stack trace.

| HTTP | `code` | Коли |
|---|---|---|
| 422 | `validation_error` | порожній/короткий текст, лише цифри, `locale` не uk/en, битий JSON, відсутнє поле |
| 401 | `unauthorized` | заданий `API_KEY`, а `X-API-Key` неправильний |
| 503 | `ai_unavailable` | модель не завантажена або впала (`AI_SIMULATE_FAILURE=true`) |
| 500 | `internal_error` | будь-яка інша непередбачена помилка |

Mobile показує `error.message` як є (`AppError.Server`), тому повідомлення пишуться українською для кінцевого користувача.
