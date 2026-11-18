# Decisions (ADR-lite)

| # | Рішення | Причина | Альтернативи |
|---|---|---|---|
| D1 | Kotlin + Jetpack Compose + Material 3 | сучасний стандарт Android, декларативний UI як SwiftUI в iOS-версії | Java + XML, Flutter |
| D2 | Власний backend FastAPI + Pydantic | рекомендовано курсом; автоматична валідація й Swagger | Ktor, Node.js |
| D3 | AI = scikit-learn TF-IDF + LogisticRegression, навчання при старті | офлайн, без ключів і витрат, мілісекунди на запит, пояснюваність через ваги | LLM API (ключі, вартість, затримка), ML Kit |
| D4 | Сума/валюта/магазин — правила (regex + словник) | детерміновано, легко тестувати; ML тут не потрібен | NER-модель |
| D5 | Поріг `AI_MIN_CONFIDENCE=0.30` → `other` + вибір вручну | краще чесно не знати, ніж вигадати категорію | завжди повертати топ-1 |
| D6 | Єдиний формат помилок `{"error":{code,message,field}}` | клієнт показує `message` без розбору різних форматів FastAPI | стандартний `detail` FastAPI |
| D7 | Retrofit + kotlinx.serialization, `ignoreUnknownKeys` | нові поля backend не ламають клієнт | Gson, Ktor client |
| D8 | ViewModel → Repository без use cases і Hilt | MVP з одним сценарієм; менше коду | повна Clean Architecture з ЛР1 |
| D9 | Конфіг: `.env` (backend), `local.properties` → `BuildConfig` (mobile) | секрети поза Git | хардкод |
| D10 | Версії AGP 9.5.1 / Gradle 9.6 / Kotlin 2.2.10 | збігаються з установленою Android Studio, без докачування | AGP 8.x |
| D11 | Тунель `adb reverse` для dev-мережі | `10.0.2.2` давав тайм-аут; тунель працює й для USB-телефона | 10.0.2.2, IP у Wi‑Fi |
| D12 | Категорії = категорії iOS-версії | одна модель даних для двох платформ | окремий набір |
