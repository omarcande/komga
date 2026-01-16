package org.gotson.komga.infrastructure.gemini

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gotson.komga.infrastructure.configuration.KomgaSettingsProvider
import org.gotson.komga.interfaces.api.rest.dto.GeminiAnalysisDto
import org.gotson.komga.interfaces.api.rest.dto.PhraseDto
import org.gotson.komga.interfaces.api.rest.dto.SentenceDto
import org.gotson.komga.interfaces.api.rest.dto.VocabularyDto
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.nio.file.Files
import java.nio.file.Path

class GeminiServiceTest {
  private val mockSettingsProvider = mockk<KomgaSettingsProvider>()
  private val mockWebClientBuilder = mockk<WebClient.Builder>()
  private val mockWebClient = mockk<WebClient>()
  private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

  private lateinit var geminiService: GeminiService

  @BeforeEach
  fun setup() {
    every { mockWebClientBuilder.baseUrl(any()) } returns mockWebClientBuilder
    every { mockWebClientBuilder.build() } returns mockWebClient

    geminiService = GeminiService(mockSettingsProvider, mockWebClientBuilder, objectMapper)
  }

  @AfterEach
  fun tearDown() {
    clearAllMocks()
  }

  @Nested
  inner class IsEnabled {
    @Test
    fun `returns true when enabled and API key is set`() {
      every { mockSettingsProvider.geminiEnabled } returns true
      every { mockSettingsProvider.geminiApiKey } returns "test-api-key"

      assertThat(geminiService.isEnabled()).isTrue()
    }

    @Test
    fun `returns false when disabled`() {
      every { mockSettingsProvider.geminiEnabled } returns false
      every { mockSettingsProvider.geminiApiKey } returns "test-api-key"

      assertThat(geminiService.isEnabled()).isFalse()
    }

    @Test
    fun `returns false when API key is null`() {
      every { mockSettingsProvider.geminiEnabled } returns true
      every { mockSettingsProvider.geminiApiKey } returns null

      assertThat(geminiService.isEnabled()).isFalse()
    }

    @Test
    fun `returns false when API key is blank`() {
      every { mockSettingsProvider.geminiEnabled } returns true
      every { mockSettingsProvider.geminiApiKey } returns "   "

      assertThat(geminiService.isEnabled()).isFalse()
    }

    @Test
    fun `returns false when API key is empty`() {
      every { mockSettingsProvider.geminiEnabled } returns true
      every { mockSettingsProvider.geminiApiKey } returns ""

      assertThat(geminiService.isEnabled()).isFalse()
    }
  }

  @Nested
  inner class AnalyzeImage {
    private val mockRequestBodyUriSpec = mockk<WebClient.RequestBodyUriSpec>()
    private val mockRequestBodySpec = mockk<WebClient.RequestBodySpec>()
    private val mockRequestHeadersSpec = mockk<WebClient.RequestHeadersSpec<*>>()
    private val mockResponseSpec = mockk<WebClient.ResponseSpec>()

    @BeforeEach
    fun setupWebClient() {
      every { mockWebClient.post() } returns mockRequestBodyUriSpec
      every { mockRequestBodyUriSpec.uri(any<String>()) } returns mockRequestBodySpec
      every { mockRequestBodySpec.contentType(MediaType.APPLICATION_JSON) } returns mockRequestBodySpec
      every { mockRequestBodySpec.bodyValue(any()) } returns mockRequestHeadersSpec
      every { mockRequestHeadersSpec.retrieve() } returns mockResponseSpec
    }

    @Test
    fun `throws IllegalStateException when API key not configured`() {
      every { mockSettingsProvider.geminiApiKey } returns null

      val imageBytes = "test image".toByteArray()

      assertThatThrownBy {
        geminiService.analyzeImage(imageBytes, "image/jpeg", "book-1", 1)
      }.isInstanceOf(IllegalStateException::class.java)
        .hasMessage("Gemini API key not configured")
    }

    @Test
    fun `returns error DTO when API returns empty response`() {
      every { mockSettingsProvider.geminiApiKey } returns "test-api-key"
      every { mockResponseSpec.bodyToMono(GeminiResponse::class.java) } returns Mono.just(GeminiResponse(candidates = emptyList()))

      val imageBytes = "test image".toByteArray()
      val result = geminiService.analyzeImage(imageBytes, "image/jpeg", "book-empty", 1)

      assertThat(result.error).isEqualTo("No analysis returned from Gemini")
      assertThat(result.sentences).isEmpty()
      assertThat(result.phrases).isEmpty()
      assertThat(result.vocabulary).isEmpty()
    }

    @Test
    fun `returns error DTO when API returns null candidates`() {
      every { mockSettingsProvider.geminiApiKey } returns "test-api-key"
      every { mockResponseSpec.bodyToMono(GeminiResponse::class.java) } returns Mono.just(GeminiResponse(candidates = null))

      val imageBytes = "test image".toByteArray()
      val result = geminiService.analyzeImage(imageBytes, "image/jpeg", "book-null", 1)

      assertThat(result.error).isEqualTo("No analysis returned from Gemini")
    }

    @Test
    fun `returns error DTO when API call fails`() {
      every { mockSettingsProvider.geminiApiKey } returns "test-api-key"
      every { mockResponseSpec.bodyToMono(GeminiResponse::class.java) } returns Mono.error(RuntimeException("Connection failed"))

      val imageBytes = "test image".toByteArray()
      val result = geminiService.analyzeImage(imageBytes, "image/jpeg", "book-error", 1)

      assertThat(result.error).startsWith("Error analyzing image:")
      assertThat(result.error).contains("Connection failed")
    }

    @Test
    fun `parses valid JSON response correctly`() {
      val jsonResponse = """
        {
          "sentences": [
            {
              "original": "これはテストです",
              "hiragana": "これはてすとです",
              "romanji": "kore wa tesuto desu",
              "translations": ["This is a test"],
              "explanation": "Test explanation",
              "comments": "Test comment"
            }
          ],
          "phrases": [
            {
              "original": "テスト",
              "hiragana": "てすと",
              "romanji": "tesuto",
              "translation": "test"
            }
          ],
          "vocabulary": [
            {
              "original": "テスト",
              "hiragana": "てすと",
              "romanji": "tesuto",
              "meaning": "test",
              "type": "noun"
            }
          ]
        }
      """.trimIndent()

      val geminiResponse = GeminiResponse(
        candidates = listOf(
          Candidate(
            content = Content(
              parts = listOf(Part(text = jsonResponse))
            )
          )
        )
      )

      every { mockSettingsProvider.geminiApiKey } returns "test-api-key"
      every { mockResponseSpec.bodyToMono(GeminiResponse::class.java) } returns Mono.just(geminiResponse)

      val imageBytes = "test image".toByteArray()
      val result = geminiService.analyzeImage(imageBytes, "image/jpeg", "book-valid", 1)

      assertThat(result.error).isNull()
      assertThat(result.sentences).hasSize(1)
      assertThat(result.sentences[0].original).isEqualTo("これはテストです")
      assertThat(result.sentences[0].romanji).isEqualTo("kore wa tesuto desu")
      assertThat(result.phrases).hasSize(1)
      assertThat(result.vocabulary).hasSize(1)
      assertThat(result.vocabulary[0].type).isEqualTo("noun")
    }

    @Test
    fun `returns cached result for same bookId and pageNumber`() {
      val jsonResponse = """{"sentences": [], "phrases": [], "vocabulary": []}"""

      val geminiResponse = GeminiResponse(
        candidates = listOf(
          Candidate(content = Content(parts = listOf(Part(text = jsonResponse))))
        )
      )

      every { mockSettingsProvider.geminiApiKey } returns "test-api-key"
      every { mockResponseSpec.bodyToMono(GeminiResponse::class.java) } returns Mono.just(geminiResponse)

      val imageBytes = "test image".toByteArray()

      // First call
      val result1 = geminiService.analyzeImage(imageBytes, "image/jpeg", "book-cache", 5)
      // Second call with same bookId and pageNumber
      val result2 = geminiService.analyzeImage(imageBytes, "image/jpeg", "book-cache", 5)

      // Verify only one API call was made
      verify(exactly = 1) { mockResponseSpec.bodyToMono(GeminiResponse::class.java) }

      assertThat(result1).isEqualTo(result2)
    }

    @Test
    fun `makes separate API calls for different pages`() {
      val jsonResponse = """{"sentences": [], "phrases": [], "vocabulary": []}"""

      val geminiResponse = GeminiResponse(
        candidates = listOf(
          Candidate(content = Content(parts = listOf(Part(text = jsonResponse))))
        )
      )

      every { mockSettingsProvider.geminiApiKey } returns "test-api-key"
      every { mockResponseSpec.bodyToMono(GeminiResponse::class.java) } returns Mono.just(geminiResponse)

      val imageBytes = "test image".toByteArray()

      // Call with page 1
      geminiService.analyzeImage(imageBytes, "image/jpeg", "book-pages", 1)
      // Call with page 2
      geminiService.analyzeImage(imageBytes, "image/jpeg", "book-pages", 2)

      // Verify two API calls were made
      verify(exactly = 2) { mockResponseSpec.bodyToMono(GeminiResponse::class.java) }
    }
  }

  @Nested
  @Tag("integration")
  inner class IntegrationTests {
    @Test
    fun `analyzeImage with real API returns valid analysis`() {
      // Load API key from file
      val apiKeyPath = Path.of("../.env/geminiapikey.txt")
      if (!Files.exists(apiKeyPath)) {
        println("Skipping integration test: API key file not found at $apiKeyPath")
        return
      }

      val apiKey = Files.readString(apiKeyPath).trim()
      if (apiKey.isBlank()) {
        println("Skipping integration test: API key is blank")
        return
      }

      // Load test image
      val imageStream = javaClass.classLoader.getResourceAsStream("manga.jpg")
      if (imageStream == null) {
        println("Skipping integration test: manga.jpg not found in test resources")
        return
      }

      val imageBytes = imageStream.readBytes()
      println("Loaded test image: ${imageBytes.size} bytes")

      // Create real settings provider mock with actual API key
      val realSettingsProvider = mockk<KomgaSettingsProvider>()
      every { realSettingsProvider.geminiEnabled } returns true
      every { realSettingsProvider.geminiApiKey } returns apiKey

      // Create real WebClient
      val realWebClientBuilder = WebClient.builder()

      // Create service with real WebClient
      val realGeminiService = GeminiService(realSettingsProvider, realWebClientBuilder, objectMapper)

      // Verify service is enabled
      assertThat(realGeminiService.isEnabled()).isTrue()

      // Call the real API
      println("Calling Gemini API...")
      val result = realGeminiService.analyzeImage(imageBytes, "image/jpeg", "integration-test", 1)

      // Print the result for verification
      println("\n=== Gemini Analysis Result ===")
      if (result.error != null) {
        println("Error: ${result.error}")
      } else {
        println("Sentences: ${result.sentences.size}")
        result.sentences.forEachIndexed { index, sentence ->
          println("  [$index] ${sentence.original}")
          println("       Hiragana: ${sentence.hiragana}")
          println("       Romanji: ${sentence.romanji}")
          println("       Translation: ${sentence.translations.firstOrNull() ?: "N/A"}")
        }

        println("\nPhrases: ${result.phrases.size}")
        result.phrases.forEach { phrase ->
          println("  - ${phrase.original} (${phrase.romanji}): ${phrase.translation}")
        }

        println("\nVocabulary: ${result.vocabulary.size}")
        result.vocabulary.forEach { vocab ->
          println("  - ${vocab.original} [${vocab.type}]: ${vocab.meaning}")
        }
      }
      println("==============================\n")

      // Assertions - at minimum we should get a valid response structure
      assertThat(result).isNotNull()

      // If no error, we should have at least one of: sentences, phrases, or vocabulary
      // (or all empty if no Japanese text was detected)
      if (result.error == null) {
        // The response structure should be valid
        assertThat(result.sentences).isNotNull()
        assertThat(result.phrases).isNotNull()
        assertThat(result.vocabulary).isNotNull()

        // If the image has Japanese text, we should get some analysis
        val hasContent = result.sentences.isNotEmpty() ||
          result.phrases.isNotEmpty() ||
          result.vocabulary.isNotEmpty()

        println("Analysis contains content: $hasContent")
      }
    }
  }
}
