package com.aurawallet.app

import com.aurawallet.app.data.mapper.toDomain
import com.aurawallet.app.data.mapper.toDto
import com.aurawallet.app.data.remote.CategorizeRequestDto
import com.aurawallet.app.data.remote.CategorizeResponseDto
import com.aurawallet.app.data.remote.Network
import com.aurawallet.app.domain.model.CategorizationInput
import com.aurawallet.app.domain.model.ConfidenceSource
import com.aurawallet.app.domain.model.PersonalExample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** ЛР6: нові поля контракту API на клієнті. */
class PersonalizationMappingTest {
    @Test
    fun requestContainsPersonalExamplesOnlyWhenPresent() {
        val with = Network.json.encodeToString(CategorizeRequestDto.serializer(),
            CategorizationInput("Епіцентр 640", personalExamples = listOf(PersonalExample("Епіцентр 1200", "housing"))).toDto())
        assertTrue(with.contains("\"personal_examples\":[{\"text\":\"Епіцентр 1200\",\"category\":\"housing\"}]"))
        val without = Network.json.encodeToString(CategorizeRequestDto.serializer(), CategorizationInput("кава 85").toDto())
        assertFalse(without.contains("personal_examples"))
    }

    @Test
    fun personalizedResponseIsMapped() {
        val json = """{"category":"housing","label":"Житло","confidence":0.87,"is_income":false,"explanation":"x",
            "confidence_source":"personal","personalization":{"matched_text":"Епіцентр 1200","matched_category":"housing",
            "similarity":0.87,"model_category":"shopping","model_label":"Покупки"}}"""
        val r = Network.json.decodeFromString(CategorizeResponseDto.serializer(), json).toDomain()
        assertEquals(ConfidenceSource.Personal, r.confidenceSource)
        assertEquals("shopping", r.personalization!!.modelCategory)
    }

    @Test
    fun oldBackendResponseStillWorks() {
        val json = """{"category":"coffee","label":"Кава","confidence":0.95,"is_income":false,"explanation":"x"}"""
        val r = Network.json.decodeFromString(CategorizeResponseDto.serializer(), json).toDomain()
        assertEquals(ConfidenceSource.Model, r.confidenceSource)
        assertNull(r.personalization)
    }
}
