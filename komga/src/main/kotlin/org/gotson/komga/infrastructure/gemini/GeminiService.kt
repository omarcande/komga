package org.gotson.komga.infrastructure.gemini

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.oshai.kotlinlogging.KotlinLogging
import org.gotson.komga.infrastructure.configuration.KomgaSettingsProvider
import org.gotson.komga.interfaces.api.rest.dto.GeminiAnalysisDto
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.util.Base64
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

@Service
class GeminiService(
  private val komgaSettingsProvider: KomgaSettingsProvider,
  webClientBuilder: WebClient.Builder,
  private val objectMapper: ObjectMapper,
) {
  private val webClient =
    webClientBuilder
      .baseUrl("https://generativelanguage.googleapis.com/v1beta")
      .build()

  private val cache =
    Caffeine
      .newBuilder()
      .expireAfterAccess(1, TimeUnit.HOURS)
      .maximumSize(100)
      .build<String, GeminiAnalysisDto>()

  fun isEnabled(): Boolean = komgaSettingsProvider.geminiEnabled && !komgaSettingsProvider.geminiApiKey.isNullOrBlank()

  fun analyzeImage(
    imageBytes: ByteArray,
    mimeType: String,
    bookId: String,
    pageNumber: Int,
  ): GeminiAnalysisDto {
    val cacheKey = "$bookId-$pageNumber"

    return cache.get(cacheKey) {
      performAnalysis(imageBytes, mimeType)
    }!!
  }

  private fun performAnalysis(
    imageBytes: ByteArray,
    mimeType: String,
  ): GeminiAnalysisDto {
    val apiKey = komgaSettingsProvider.geminiApiKey ?: throw IllegalStateException("Gemini API key not configured")
    val model = GEMINI_MODEL

    val base64Image = Base64.getEncoder().encodeToString(imageBytes)

    val requestBody =
      mapOf(
        "contents" to
          listOf(
            mapOf(
              "parts" to
                listOf(
                  mapOf("text" to ANALYSIS_PROMPT),
                  mapOf(
                    "inline_data" to
                      mapOf(
                        "mime_type" to mimeType,
                        "data" to base64Image,
                      ),
                  ),
                ),
            ),
          ),
        "generationConfig" to
          mapOf(
            "responseMimeType" to "application/json",
          ),
      )

    try {
      logger.info { "Sending image analysis request to Gemini API" }

      val response =
        webClient
          .post()
          .uri("/models/$model:generateContent?key=$apiKey")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(requestBody)
          .retrieve()
          .bodyToMono(GeminiResponse::class.java)
          .block()

      val textContent =
        response
          ?.candidates
          ?.firstOrNull()
          ?.content
          ?.parts
          ?.firstOrNull()
          ?.text

      if (textContent.isNullOrBlank()) {
        logger.warn { "Gemini API returned empty response" }
        return GeminiAnalysisDto(error = "No analysis returned from Gemini")
      }

      logger.debug { "Gemini response: $textContent" }

      return objectMapper.readValue(textContent, GeminiAnalysisDto::class.java)
    } catch (e: Exception) {
      logger.error(e) { "Error calling Gemini API" }
      return GeminiAnalysisDto(error = "Error analyzing image: ${e.message}")
    }
  }

  companion object {
    const val GEMINI_MODEL = "gemini-2.5-flash"
    const val ANALYSIS_PROMPT =
      """You are a Japanese language tutor analyzing a page from a manga.

Analyze any Japanese text visible in this image and return your analysis as a JSON object.

IMPORTANT: You MUST return ONLY valid JSON. Do not include any markdown, explanations, or text outside the JSON structure.

Return the following JSON structure:

{
  "sentences": [
    {
      "original": "The original Japanese sentence exactly as shown",
      "hiragana": "Full sentence converted to hiragana for reading practice",
      "romanji": "Full romanized version using Hepburn romanization",
      "translations": ["Primary English translation", "Alternative translation if applicable"],
      "explanation": "Markdown-formatted grammar breakdown (see guidelines below)",
      "comments": "Cultural context, game-specific context, formality level, or other relevant notes"
    }
  ],
  "phrases": [
    {
      "original": "Japanese phrase (2+ words that form a unit but not a complete sentence)",
      "hiragana": "Hiragana reading",
      "romanji": "Romanized version",
      "translation": "English meaning"
    }
  ],
  "vocabulary": [
    {
      "original": "Individual word or kanji",
      "hiragana": "Hiragana reading",
      "romanji": "Romanization",
      "meaning": "English definition(s)",
      "type": "noun OR verb OR adjective OR adverb"
    }
  ]
}

Guidelines:

1. SENTENCES: Complete grammatical sentences with subject/verb. For the explanation field, use markdown formatting with bullet points for each word:
   - Start each word breakdown with "- " (dash space)
   - Bold the Japanese word using **word**
   - Include reading in parentheses
   - Explain the meaning and grammatical role
   Example explanation value:
   "- **この** (kono) - 'this' - demonstrative adjective modifying the noun\\n- **リスト** (risuto) - 'list' - noun, loanword from English\\n- **に** (ni) - location particle indicating where something exists\\n- **は** (wa) - topic marker, emphasizes the list as the topic"

2. PHRASES: Multi-word expressions that aren't complete sentences (e.g., menu items, labels, compound expressions).

3. VOCABULARY: Extract unique CONTENT WORDS only from the detected text. Include ONLY:
   - Nouns (type: "noun")
   - Verbs in dictionary form (type: "verb")
   - I-adjectives and na-adjectives (type: "adjective")
   - Adverbs (type: "adverb")

   DO NOT include:
   - Particles (は, が, を, に, で, と, も, etc.)
   - Proper nouns / Names of people or places
   - Pronouns (私, あなた, これ, それ, etc.)
   - Conjunctions

   DEDUPLICATE - each word should appear only once.

4. If no Japanese text is visible, return: {"sentences": [], "phrases": [], "vocabulary": [], "error": "No Japanese text detected in image."}

5. Always include all three arrays (sentences, phrases, vocabulary), never omit them.

Return ONLY the JSON object, no additional text."""
  }
}

data class GeminiResponse(
  val candidates: List<Candidate>? = null,
)

data class Candidate(
  val content: Content? = null,
)

data class Content(
  val parts: List<Part>? = null,
)

data class Part(
  val text: String? = null,
)
