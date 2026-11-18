# Testing

## Backend
```bash
cd backend
source .venv/bin/activate
pytest -q                      # усі тести
pytest -q tests/test_service.py  # без HTTP (не потребує fastapi/httpx)
```
- `tests/test_service.py` — парсер (суми, валюти, магазини), класифікатор на контрольних прикладах, low confidence, прапорець доходу, симуляція збою AI, валідація запиту.
- `tests/test_api.py` — HTTP через `TestClient`: `/health`, успіх, 422 (порожньо, відсутнє поле, лише цифри, невідома мова), битий JSON, 503.

## Mobile
```bash
cd mobile
./gradlew test            # JVM unit-тести (ParsingTest)
./gradlew assembleDebug   # перевірка компіляції
```
`ParsingTest`: розбір успішної відповіді з невідомим полем, розбір конверта помилки, битий JSON → `SerializationException`, валідація введення.

## Обов'язкові ручні сценарії (перед кожним комітом у main)
| # | Сценарій | Очікування |
|---|---|---|
| M1 | «Кава в Aroma Kava 85 грн» → Розпізнати | Кава, −₴85, Aroma Kava, ~95% |
| M2 | Порожнє поле | кнопка неактивна; Enter → «Введіть опис операції» |
| M3 | Зупинити backend → Розпізнати | «Немає з'єднання…», кнопка «Повторити» |
| M4 | `AI_SIMULATE_FAILURE=true` → Розпізнати | повідомлення «AI-сервіс тимчасово недоступний…» |
| M5 | Два запити поспіль з різним текстом | показано результат другого |
| M6 | «щось незрозуміле xyz» | «Інше», прохання обрати категорію, «Зберегти» неактивна |

## Мережа для ручних тестів
`adb reverse tcp:8000 tcp:8000` + `aura.apiBaseUrl=http://127.0.0.1:8000/` (див. README).
