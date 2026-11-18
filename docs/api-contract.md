# API contract

Base URL (dev): `http://127.0.0.1:8000/` через `adb reverse`, або `http://10.0.2.2:8000/` з емулятора. Swagger: `/docs`.
Кодування: JSON UTF-8. Необов'язковий заголовок `X-API-Key` (обов'язковий, лише якщо на сервері задано `API_KEY`).

## `GET /health`
200:
```json
{ "status": "ok", "version": "0.2.0", "model_loaded": true, "model_classes": 17, "training_samples": 201 }
```
`status = "degraded"`, якщо модель не завантажилась.

## `POST /api/v1/transactions/categorize`
Запит `CategorizeRequest`:
| Поле | Тип | Обов'язкове | Обмеження |
|---|---|---|---|
| `text` | string | так | 2–200 символів після нормалізації пробілів, має містити хоча б одну літеру |
| `locale` | `"uk"` \| `"en"` | ні (`uk`) | мова `label` і `explanation` |

Відповідь 200 `CategorizeResponse`:
| Поле | Тип | Опис |
|---|---|---|
| `category` | string | код категорії або `other` |
| `label` | string | назва категорії мовою `locale` |
| `confidence` | number 0..1 | ймовірність найкращої категорії (навіть якщо повернуто `other`) |
| `is_income` | bool | `true` для `salary`, `freelance`, `cashback` |
| `amount` | number \| null | сума з тексту |
| `currency` | `UAH`/`USD`/`EUR`/`PLN` \| null | |
| `merchant` | string \| null | відомий магазин зі словника |
| `explanation` | string | людське пояснення |
| `alternatives` | `[{category,label,confidence}]` | до 3 варіантів |
| `low_confidence` | bool | модель невпевнена → обрати вручну |
| `processing_ms` | number | час обробки на сервері |

Mobile ігнорує невідомі поля (`ignoreUnknownKeys = true`), тож додавання нових полів у відповідь — сумісна зміна. Видалення чи перейменування — несумісна, потребує оновлення `ApiModels.kt` і цього документа.

## Помилки
Завжди `{"error": {"code": string, "message": string, "field"?: string}}`, без stack trace.

| HTTP | `code` | Коли |
|---|---|---|
| 422 | `validation_error` | порожній/короткий текст, лише цифри, `locale` не uk/en, битий JSON, відсутнє поле |
| 401 | `unauthorized` | заданий `API_KEY`, а `X-API-Key` неправильний |
| 503 | `ai_unavailable` | модель не завантажена або впала (`AI_SIMULATE_FAILURE=true`) |
| 500 | `internal_error` | будь-яка інша непередбачена помилка |

Mobile показує `error.message` як є (`AppError.Server`), тому повідомлення пишуться українською для кінцевого користувача.
