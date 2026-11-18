"""Виділення сутностей з опису операції: сума, валюта, магазин.

Працює на правилах (регулярні вирази + словник відомих магазинів), без мережі.
"""
from __future__ import annotations

import re
from dataclasses import dataclass

# Синоніми валют → ISO-код.
_CURRENCY_WORDS = {
    "грн": "UAH", "гривень": "UAH", "гривні": "UAH", "гривня": "UAH", "₴": "UAH", "uah": "UAH", "hrn": "UAH",
    "$": "USD", "usd": "USD", "дол": "USD", "долар": "USD", "доларів": "USD", "долари": "USD",
    "€": "EUR", "eur": "EUR", "євро": "EUR", "euro": "EUR",
    "zł": "PLN", "pln": "PLN", "злотих": "PLN",
}
_CURRENCY_RE = "|".join(sorted((re.escape(k) for k in _CURRENCY_WORDS), key=len, reverse=True))
# Число: 85 | 486,30 | 1 250 | 1250.5
_NUMBER_RE = r"\d{1,3}(?:[  ]\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?"
_AMOUNT_WITH_CURRENCY = re.compile(
    rf"(?:(?P<cur1>{_CURRENCY_RE})\s?(?P<num1>{_NUMBER_RE}))|(?:(?P<num2>{_NUMBER_RE})\s?(?P<cur2>{_CURRENCY_RE})(?!\w))",
    re.IGNORECASE,
)
_ANY_NUMBER = re.compile(rf"(?<![\w.,])(?:{_NUMBER_RE})(?![\w])")

KNOWN_MERCHANTS = [
    "Сільпо", "АТБ", "Новус", "Ашан", "Метро", "Фора", "Варус", "Rozetka Fresh", "Rozetka", "Glovo", "Bolt Food",
    "Puzata Hata", "Пузата Хата", "McDonald's", "KFC", "Celentano", "Aroma Kava", "Lviv Croissants", "Starbucks",
    "Coffee Point", "One Love", "Світ Кави", "Uklon", "Bolt", "Uber", "ОККО", "OKKO", "WOG", "Shell", "Multiplex",
    "Planeta Kino", "Steam", "PlayStation Store", "Netflix", "Spotify", "YouTube Premium", "Apple Music", "iCloud",
    "Megogo", "ChatGPT", "Google One", "Figma", "Adobe", "Sweet.tv", "Київстар", "Vodafone", "lifecell", "АНЦ",
    "Подорожник", "Zara", "H&M", "Reserved", "Intertop", "Answear", "Booking", "Airbnb", "Укрзалізниця",
    "Інтерсіті", "FlixBus", "Ryanair", "Епіцентр", "OLX", "Prom.ua", "Comfy", "Фокстрот", "Amazon", "AliExpress",
    "JYSK", "IKEA", "Eva", "Watsons", "Upwork", "Fiverr",
]


@dataclass
class ParsedEntities:
    amount: float | None
    currency: str | None
    merchant: str | None
    cleaned_text: str  # текст без суми й валюти — йде на вхід класифікатора


def _to_float(raw: str) -> float:
    return float(raw.replace(" ", "").replace(" ", "").replace(",", "."))


def parse(text: str) -> ParsedEntities:
    amount: float | None = None
    currency: str | None = None
    cleaned = text

    match = _AMOUNT_WITH_CURRENCY.search(text)
    if match:
        num = match.group("num1") or match.group("num2")
        cur = (match.group("cur1") or match.group("cur2")).lower()
        amount = _to_float(num)
        currency = _CURRENCY_WORDS.get(cur, "UAH")
        cleaned = text[: match.start()] + " " + text[match.end():]
    else:
        numbers = list(_ANY_NUMBER.finditer(text))
        if numbers:
            # Без валюти беремо останнє число: «2 кави 170» → 170 грн.
            last = numbers[-1]
            amount = _to_float(last.group(0))
            currency = "UAH"
            cleaned = text[: last.start()] + " " + text[last.end():]

    merchant = None
    lowered = text.lower()
    for name in KNOWN_MERCHANTS:  # довші назви стоять раніше за коротші (Rozetka Fresh → Rozetka)
        if re.search(rf"(?<!\w){re.escape(name.lower())}(?!\w)", lowered):
            merchant = name
            break

    cleaned = re.sub(r"\s+", " ", cleaned).strip()
    return ParsedEntities(amount=amount, currency=currency, merchant=merchant, cleaned_text=cleaned or text)
