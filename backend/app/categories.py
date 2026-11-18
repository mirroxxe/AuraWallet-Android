"""Категорії операцій. Збігаються з категоріями iOS-версії AuraWallet."""

CATEGORY_LABELS: dict[str, dict[str, str]] = {
    "groceries": {"uk": "Продукти", "en": "Groceries"},
    "cafes": {"uk": "Кафе", "en": "Cafés"},
    "coffee": {"uk": "Кава", "en": "Coffee"},
    "transport": {"uk": "Транспорт", "en": "Transport"},
    "entertainment": {"uk": "Розваги", "en": "Entertainment"},
    "subscriptions": {"uk": "Підписки", "en": "Subscriptions"},
    "housing": {"uk": "Житло", "en": "Housing"},
    "health": {"uk": "Здоров'я", "en": "Health"},
    "clothing": {"uk": "Одяг", "en": "Clothing"},
    "gifts": {"uk": "Подарунки", "en": "Gifts"},
    "travel": {"uk": "Подорожі", "en": "Travel"},
    "mobile": {"uk": "Зв'язок", "en": "Mobile"},
    "shopping": {"uk": "Покупки", "en": "Shopping"},
    "transfers": {"uk": "Перекази", "en": "Transfers"},
    "salary": {"uk": "Зарплата", "en": "Salary"},
    "freelance": {"uk": "Фриланс", "en": "Freelance"},
    "cashback": {"uk": "Кешбек", "en": "Cashback"},
    "other": {"uk": "Інше", "en": "Other"},
}

INCOME_CATEGORIES = {"salary", "freelance", "cashback"}


def label(category: str, locale: str = "uk") -> str:
    labels = CATEGORY_LABELS.get(category, CATEGORY_LABELS["other"])
    return labels.get(locale, labels["uk"])
