package com.aurawallet.app

import com.aurawallet.app.data.remote.CategorizeResponseDto
import com.aurawallet.app.data.remote.ErrorEnvelopeDto
import com.aurawallet.app.data.remote.Network
import com.aurawallet.app.data.mapper.toDomain
import com.aurawallet.app.domain.usecase.InputValidator
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Перевіряє перетворення JSON → модель і валідацію введення (без емулятора). */
class ParsingTest {

    @Test
    fun parsesSuccessResponse() {
        val json = """{"category":"coffee","label":"Кава","confidence":0.95,"is_income":false,"amount":85.0,
            "currency":"UAH","merchant":"Aroma Kava","explanation":"...","alternatives":[],"low_confidence":false,
            "processing_ms":3.5,"new_field_from_future":1}"""
        val r = Network.json.decodeFromString(CategorizeResponseDto.serializer(), json).toDomain()
        assertEquals("coffee", r.category)
        assertEquals(85.0, r.amount!!, 0.0)
    }

    @Test
    fun parsesErrorEnvelope() {
        val json = """{"error":{"code":"validation_error","message":"Опис не може бути порожнім","field":"text"}}"""
        val e = Network.json.decodeFromString(ErrorEnvelopeDto.serializer(), json)
        assertEquals("text", e.error.field)
    }

    @Test(expected = SerializationException::class)
    fun brokenJsonThrows() {
        Network.json.decodeFromString(CategorizeResponseDto.serializer(), "{broken")
    }

    @Test
    fun validation() {
        assertNotNull(InputValidator.validate(""))
        assertNotNull(InputValidator.validate("12345"))
        assertNull(InputValidator.validate("кава 85"))
    }
}
