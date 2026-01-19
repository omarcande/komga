package org.gotson.komga.infrastructure.gemini

import com.fasterxml.jackson.core.JsonProcessingException
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
    refresh: Boolean = false,
    mangaFilename: String? = null,
  ): GeminiAnalysisDto {
    val cacheKey = "$bookId-$pageNumber"

    if (refresh) {
      cache.invalidate(cacheKey)
    }

    return cache.get(cacheKey) {
      performAnalysis(imageBytes, mimeType, pageNumber, mangaFilename)
    }!!
  }

  private fun performAnalysis(
    imageBytes: ByteArray,
    mimeType: String,
    pageNumber: Int,
    mangaFilename: String?,
  ): GeminiAnalysisDto {
    val apiKey = komgaSettingsProvider.geminiApiKey ?: throw IllegalStateException("Gemini API key not configured")
    val model = GEMINI_MODEL

    val base64Image = Base64.getEncoder().encodeToString(imageBytes)

    val prompt = buildPrompt(pageNumber, mangaFilename)

    val requestBody =
      mapOf(
        "contents" to
          listOf(
            mapOf(
              "parts" to
                listOf(
                  mapOf("text" to prompt),
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

      return try {
        objectMapper.readValue(textContent, GeminiAnalysisDto::class.java)
      } catch (e: JsonProcessingException) {
        logger.warn(e) { "Failed to parse Gemini response as JSON: $textContent" }
        GeminiAnalysisDto(error = "Failed to parse AI response. The AI returned an invalid format. Please try again.")
      }
    } catch (e: Exception) {
      logger.error(e) { "Error calling Gemini API" }
      return GeminiAnalysisDto(error = "Error analyzing image: ${e.message}")
    }
  }

  private fun buildPrompt(
    pageNumber: Int,
    mangaFilename: String?,
  ): String {
    val contextLine =
      if (mangaFilename != null) {
        "You are analyzing page $pageNumber of the manga: $mangaFilename\n\n"
      } else {
        "You are analyzing page $pageNumber of a manga.\n\n"
      }
    return contextLine + ANALYSIS_PROMPT
  }

  companion object {
    const val GEMINI_MODEL = "gemini-2.5-flash"
    const val ANALYSIS_PROMPT =
      """You are a Japanese language tutor analyzing a page from a manga. Your task is to identify panels and extract dialogue while preserving the conversation flow.

IMPORTANT: You MUST return ONLY valid JSON. Do not include any markdown, explanations, or text outside the JSON structure.

Return the following JSON structure:

{
  "panels": [
    {
      "panelNumber": 1,
      "description": "Brief visual description of the panel",
      "context": null,
      "dialogues": [
        {
          "speakerHint": "Character name or description (optional)",
          "original": "Japanese dialogue exactly as shown",
          "hiragana": "Full dialogue converted to hiragana",
          "romanji": "Romanized version using Hepburn romanization",
          "translations": ["English translation"],
          "explanation": "Markdown-formatted grammar breakdown",
          "comments": "Cultural context, tone, or other notes"
        }
      ]
    }
  ],
  "vocabulary": [
    {
      "original": "Individual word",
      "hiragana": "Hiragana reading",
      "romanji": "Romanization",
      "meaning": "English definition",
      "type": "noun|verb|adjective|adverb"
    }
  ],
  "pageSummary": "1-2 sentence summary of what happens on this page"
}

## Field Definitions

### Panel Level Fields

**panelNumber** (required): Reading order number starting from 1. For Japanese manga, read right-to-left, top-to-bottom.

**description** (required): Brief visual description of what's happening in the panel.
- Always provide a non-empty string
- Examples: "Close-up of a girl looking surprised", "Two characters talking in a classroom"

**context** (required for panels 2+): Explains how this panel relates to previous panels.
- Use null ONLY for the first panel
- For all other panels, provide a non-empty string
- Examples: "Response to the question in panel 1", "Continuing the explanation", "New scene - character is now alone"

### Dialogue Level Fields

**speakerHint** (optional): Identifies who is speaking.
- Use null if only one character speaks or speaker is unclear
- Use character name if known from context
- Use descriptive identifier if name unknown: "Girl with long hair", "Boy 1", "Teacher", "Narrator"

**original** (required): The exact Japanese text as shown in the speech bubble.

**hiragana** (required): Full dialogue converted to hiragana for reading practice.

**romanji** (required): Romanized version using Hepburn romanization.

**translations** (required): Array with primary English translation, optionally with alternatives.

**explanation** (required): Grammar breakdown using markdown formatting with bullet points:
- Start each word breakdown with "- " (dash space)
- Bold the Japanese word using **word**
- Include reading in parentheses
- Explain meaning and grammatical role
Example: "- **この** (kono) - 'this' - demonstrative adjective\\n- **本** (hon) - 'book' - noun\\n- **を** (wo) - object marker particle"

**comments** (optional): Cultural context, formality level, tone notes, or other relevant information.

## Guidelines

1. PANELS: Identify each distinct panel in reading order. Japanese manga reads right-to-left, top-to-bottom.

2. DIALOGUES: Extract all speech bubbles within each panel, in reading order. Include:
   - Character dialogue
   - Thought bubbles
   - Narration boxes
   - Sound effects with meaning (if textual)

3. VOCABULARY: Extract unique CONTENT WORDS from all dialogues. Include ONLY:
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

4. PAGE SUMMARY: Write 1-2 sentences summarizing the narrative content of the page.

5. If no Japanese text is visible, return:
   {"panels": [], "vocabulary": [], "pageSummary": null, "error": "No Japanese text detected in image."}

6. Always include all required arrays (panels, vocabulary), never omit them.

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
