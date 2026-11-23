package com.aurawallet.app.domain.model

/**
 * Визначені категорії помилок, зрозумілі інтерфейсу. Технічні винятки (IOException, HttpException,
 * SerializationException…) перетворюються на ці категорії в шарі даних і далі не проходять.
 */
sealed class AppError(val userMessage: String) {
    /** Некоректне введення: запит до сервера не надсилається. */
    data class Validation(val reason: Reason) : AppError(reason.message) {
        enum class Reason(val message: String) {
            Empty("Введіть опис операції"),
            TooShort("Опис занадто короткий"),
            NoWords("Додайте хоча б одне слово, напр. «кава 85»"),
            TooLong("Опис задовгий — максимум 200 символів"),
        }
    }
    data object Network : AppError("Немає з'єднання з сервером. Перевірте, що backend запущено, і спробуйте ще раз.")
    data object Timeout : AppError("Сервер не відповів вчасно. Спробуйте ще раз.")
    data object BadResponse : AppError("Сервер повернув неочікувану відповідь. Спробуйте ще раз.")
    class Server(message: String, val code: String) : AppError(message)
    /** Непередбачена помилка: показуємо нейтральний текст замість «немає з'єднання». */
    data object Unknown : AppError("Щось пішло не так. Спробуйте ще раз.")

    /** Чи має сенс зберегти запит як «очікує» й надіслати пізніше. */
    val isConnectivity: Boolean get() = this is Network || this is Timeout
}

/** Носій [AppError] усередині `Result.failure`. */
class AppErrorException(val error: AppError) : Exception(error.userMessage)

fun Throwable.toAppError(): AppError = (this as? AppErrorException)?.error ?: AppError.Unknown
