# ЛР4 — план: локальна історія запитів (Room)

## Аналіз поточного стану (після ЛР3)
- Результат розпізнавання живе в `ResultState.Success` у `CategorizeViewModel`; після «Скасувати» або нового запиту зникає.
- Підтверджені операції (`saved`) — `List<SavedTransaction>` у пам'яті ViewModel; після перезапуску зникають разом з бюджетом.
- Точка, де з'являється готовий результат: `repository.categorize(text).onSuccess { … }` у `CategorizeViewModel.submit()`.
- Екран один (`CategorizeScreen`); навігації немає.

## Структура запису (`history` у Room)
| Поле | Тип | Навіщо |
|---|---|---|
| `id` | Long, autoincrement | локальний ідентифікатор |
| `created_at` | Long (epoch ms) | час отримання результату (для «очікує» — час створення) |
| `input_text` | String ≤ 200 | що ввів користувач |
| `status` | `DONE` / `PENDING` / `SENDING` | готово / очікує / надсилається |
| `category`, `label`, `confidence`, `is_income`, `low_confidence` | nullable | результат AI (null для «очікує») |
| `amount`, `currency`, `merchant` | nullable | виділені сутності |
| `explanation` | String? | пояснення AI |
| `alternatives` | String? (JSON) | до 3 альтернатив для деталей |
| `processing_ms` | Double? | час обробки на сервері |
| `confirmed`, `chosen_category`, `chosen_label` | Boolean, String? | користувач натиснув «Зберегти» й обрав категорію → операція бере участь у бюджеті |

**Не зберігаємо:** помилки backend і їх тексти, HTTP-коди, stack trace, `X-API-Key`, адресу сервера, неповні/невалідні відповіді (невдалий розбір JSON не створює запису).

**Успішний результат** = HTTP 200 + відповідь розібрана в `CategorizeResponseDto`. Low confidence — теж успішна відповідь AI (категорія «Інше» + альтернативи), її зберігаємо.

## Кроки (окремі commits)
1. `build(mobile)`: Room 2.8.4 + KSP 2.3.x (обґрунтування: рекомендовано методичкою, перевірка SQL на етапі компіляції, Flow). Без інших залежностей.
2. `feat(mobile)`: `HistoryEntity`, `HistoryDao`, `AuraDatabase`, `HistoryRepository` (add / list / one / update / delete / clear) + `AuraApplication` як простий контейнер залежностей. Тест мапінгу (JVM).
3. `feat(mobile)`: інтеграція: успішний результат → запис `DONE`; «Зберегти» → `confirmed`; список для бюджету читається з Room (бюджет переживає перезапуск).
4. `feat(mobile)`: екрани «Історія» і «Деталі», порожній стан, завантаження, видалення запису й очищення з підтвердженням; перемикання екранів без нової бібліотеки навігації (`BackHandler`).
5. `feat(mobile)` (підвищений рівень): без мережі — «Зберегти в чергу» → `PENDING`; у історії «Надіслати»; `SENDING` блокує повтор; помилка → знову `PENDING`.
6. `docs`: architecture, requirements, testing, known-limitations, decisions, README.

## Критерії готовності
- Перший запуск: «Історія порожня…».
- Успішний запит → запис зверху списку; два запити → два записи, новіший зверху.
- Перезапуск застосунку → записи на місці.
- Вимкнений backend → запис «готово» не створюється.
- Видалення / очищення з підтвердженням не повертається після перезапуску.
- Сценарії ЛР2 (M1–M6) і ЛР3 (B1–B6) працюють.
