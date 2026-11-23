Версія коду: `87c331b` · модель: TF-IDF + LogisticRegression, 201 прикладів, 17 класів · AI_MIN_CONFIDENCE=0.3 · AI_PERSONAL_MIN_SIMILARITY=0.5

### Контрольні приклади (без персонального контексту)
| # | Вхід | Очікувано | Фактично | Впевненість | Сума / магазин | ✓ |
|---|---|---|---|---|---|---|
| 1 | Кава в Aroma Kava 85 грн | coffee | coffee | 0.95 | 85 UAH / Aroma Kava | ✅ |
| 2 | Uklon до роботи 142 | transport | transport | 0.63 | 142 UAH / Uklon | ✅ |
| 3 | Сільпо 486,30 | groceries | groceries | 0.87 | 486.3 UAH / Сільпо | ✅ |
| 4 | Netflix 299 грн | subscriptions | subscriptions | 0.83 | 299 UAH / Netflix | ✅ |
| 5 | зарплата за листопад 38 000 грн | salary | salary | 0.73 | 38000 UAH / — | ✅ |
| 6 | Bolt Food суші 650 | cafes | cafes | 0.73 | 650 UAH / Bolt Food | ✅ |
| 7 | Rozetka навушники $45 | shopping | shopping | 0.96 | 45 USD / Rozetka | ✅ |
| 8 | квиток на потяг до Києва 520 | travel | other (low) | 0.29 | 520 UAH / — | ❌ |
| 9 | ремонт велосипеда 300 | other | other (low) | 0.10 | 300 UAH / — | ✅ |
| 10 | щось незрозуміле xyz | other | other (low) | 0.09 | — / — | ✅ |

Точність на контрольних прикладах: **9/10**

### Персональні запити
| # | Вхід | Очікувано | Без контексту | З контекстом | Збіг з історією |
|---|---|---|---|---|---|
| 1 | Майстерня шоколаду цукерки 280 | gifts | other ❌ | gifts ✅ | «Майстерня шоколаду 320 грн» 0.89 |
| 2 | шоколад з Майстерні шоколаду 150 | gifts | other ❌ | gifts ✅ | «Майстерня шоколаду 320 грн» 0.79 |
| 3 | Епіцентр 640 грн | housing | shopping ❌ | housing ✅ | «Епіцентр фарба для стін 1200» 0.87 |
| 4 | Епіцентр лампочки 210 | housing | shopping ❌ | housing ✅ | «Епіцентр фарба для стін 1200» 0.79 |
| 5 | SportLife 900 | health | other ❌ | health ✅ | «SportLife абонемент 900» 0.54 |
| 6 | абонемент SportLife на місяць 850 | health | other ❌ | health ✅ | «SportLife абонемент 900» 0.80 |
| 7 | Укрпошта 60 | shopping | other ❌ | shopping ✅ | «Укрпошта посилка 75» 0.75 |
| 8 | Книгарня Є 380 грн | entertainment | other ❌ | entertainment ✅ | «Книгарня Є 450» 1.00 |
| 9 | пральня 200 | housing | other ❌ | housing ✅ | «пральня 150» 1.00 |
| 10 | Glovo 410 | cafes | cafes ✅ | cafes ✅ | — |
| 11 | Нова пошта доставка 120 | shopping | cafes ❌ | shopping ✅ | «Нова пошта 90» 0.59 |
| 12 | бабусі подарунок 500 | gifts | gifts ✅ | gifts ✅ | — |
| 13 | шоколадка в АТБ 45 | groceries | other ❌ | other ❌ | — |
| 14 | плитка шоколаду Сільпо 60 | groceries | groceries ✅ | groceries ✅ | — |
| 15 | Епіцентр ресторан обід 300 | cafes | cafes ✅ | housing ❌ | «Епіцентр фарба для стін 1200» 0.61 |
| 16 | нова сукня 1200 | clothing | other ❌ | shopping ❌ | «Нова пошта 90» 0.73 |
| 17 | суші Bolt Food 600 | cafes | cafes ✅ | cafes ✅ | — |
| 18 | абонемент у басейн 700 | health | health ✅ | health ✅ | — |

Точність без контексту: **6/18 (33%)** · з контекстом: **15/18 (83%)**

### Регресія: контрольні приклади з персональним контекстом
Змінено результатів: **0/10**

