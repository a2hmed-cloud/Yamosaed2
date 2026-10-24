package com.yamosaed.assistant.gemini

import org.junit.Assert.*
import org.junit.Test

class GeminiModelsRepositoryTest {
    @Test fun parsesOnlyModelsSupportingGenerateContent() {
        val json = """{"models":[
          {"name":"models/gemini-a","displayName":"Gemini A","description":"chat","supportedGenerationMethods":["generateContent","countTokens"]},
          {"name":"models/embed-a","displayName":"Embed","supportedGenerationMethods":["embedContent"]},
          {"name":"models/gemini-b","supportedGenerationMethods":["generateContent"]}
        ]}"""
        val models = GeminiModelsRepository.parseModels(json)
        assertEquals(listOf("gemini-a", "gemini-b"), models.map { it.id })
        assertEquals("Gemini A", models.first().displayName)
        assertTrue(models.all { it.supportsGenerateContent })
    }

    @Test fun emptyResponseReturnsEmptyList() {
        assertTrue(GeminiModelsRepository.parseModels("""{"models":[]}""").isEmpty())
    }

    @Test fun invalidJsonThrows() {
        try {
            GeminiModelsRepository.parseModels("not-json")
            fail("Expected JSON parsing failure")
        } catch (_: Exception) { }
    }

    @Test fun selectedModelIsUsedInGenerationUrlWithoutDuplicatePrefix() {
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-test:generateContent",
            geminiGenerateContentUrl("models/gemini-test")
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-test:generateContent",
            geminiGenerateContentUrl("gemini-test")
        )
    }

    @Test fun stripsModelsPrefixOnce() {
        val models = GeminiModelsRepository.parseModels("""{"models":[{"name":"models/models/gemini-x","supportedGenerationMethods":["generateContent"]}]}""")
        assertEquals("models/gemini-x", models.single().id)
    }
}
