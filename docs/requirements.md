# Requirements

Статус: ✅ реалізовано · 🟡 частково · ⏳ не реалізовано.

| ID | Вимога | Статус | Де в коді |
|---|---|---|---|
| ФВ-1 | Додавання операції з описом, категорією, сумою | 🟡 текстовий опис + AI-категорія; збережені операції в Room (ЛР4) | `mobile/.../ui/CategorizeViewModel.kt` |
| ФВ-1a | AI визначає категорію, суму, валюту, магазин | ✅ | `backend/app/service.py`, `backend/app/ai/*` |
| ФВ-1b | Користувач може змінити категорію перед збереженням | ✅ | `ResultCard` у `CategorizeScreen.kt` |
| ФВ-2 | Головний екран з балансами | ⏳ | — |
| ФВ-3 | Місячний бюджет, попередження 80% / 100% | ✅ картка в застосунку (без системних push) | `backend/app/service.py::budget_summary`, `BudgetCard` у `CategorizeScreen.kt` |
| ФВ-4 | Накопичувальні цілі й округлення | ⏳ | — |
| ФВ-5 | Синхронізація з Monobank | ⏳ | — |
| ФВ-6 | Вхід Google, резервна копія | ⏳ | — |
| ФВ-8 | Історія успішних запитів: новіші зверху, порожній стан, деталі | ✅ ЛР4 | `ui/HistoryScreen.kt`, `data/HistoryRepository.kt` |
| ФВ-8a | Видалення одного запису й очищення всієї історії з підтвердженням | ✅ ЛР4 | `HistoryScreen.kt` (`ConfirmDialog`) |
| ФВ-8b | Дані зберігаються після перезапуску; помилки не зберігаються | ✅ ЛР4 | `data/local/*`, `CategorizeViewModel.submit` |
| ФВ-8c | Запит без мережі → «очікує», ручне «Надіслати», без подвійного надсилання | ✅ ЛР4 (підвищений рівень) | `HistoryDao.transition`, `HistoryViewModel.send` |
| ФВ-7 | Біометрія, мови, теми | 🟡 світла/темна тема за системою; мова лише українська | `ui/theme/Theme.kt` |

## Нефункціональні вимоги
- Android 8.0+ (minSdk 26), target/compile SDK 37.
- Лише дозвіл `INTERNET`.
- Секрети не в коді: `.env` (backend), `local.properties` (mobile) — обидва в `.gitignore`.
- Помилки не показують stack trace користувачу.
- Локальна база лише в приватній пам'яті застосунку; `allowBackup=false`; без токенів, ключів і текстів помилок.

## Критерії готовності (Definition of Done) для будь-якої задачі
1. `pytest -q` у `backend/` проходить.
2. Android-проєкт збирається (`./gradlew assembleDebug`) і проходять `./gradlew test`.
3. Ручний сценарій ЛР2 працює: «Кава в Aroma Kava 85 грн» → «Кава», 85 ₴, Aroma Kava.
4. API-моделі mobile (`ApiModels.kt`) і backend (`schemas.py`) сумісні.
5. Документація в `docs/` і README оновлені.
