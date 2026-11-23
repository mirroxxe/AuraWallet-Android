package com.aurawallet.app.domain.usecase

import com.aurawallet.app.domain.model.AppError
import com.aurawallet.app.domain.model.AppError.Validation.Reason

/** Правила введення опису операції. Ті самі для головного екрана й надсилання з історії. */
object InputValidator {
    const val MAX_LENGTH = 200

    fun validate(text: String): AppError.Validation? {
        val t = text.trim()
        val reason = when {
            t.isEmpty() -> Reason.Empty
            t.length < 2 -> Reason.TooShort
            t.length > MAX_LENGTH -> Reason.TooLong
            t.none { it.isLetter() } -> Reason.NoWords
            else -> null
        }
        return reason?.let { AppError.Validation(it) }
    }
}
