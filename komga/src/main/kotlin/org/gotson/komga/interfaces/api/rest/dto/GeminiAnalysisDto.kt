package org.gotson.komga.interfaces.api.rest.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class GeminiAnalysisDto(
  val sentences: List<SentenceDto> = emptyList(),
  val phrases: List<PhraseDto> = emptyList(),
  val vocabulary: List<VocabularyDto> = emptyList(),
  val error: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SentenceDto(
  val original: String,
  val hiragana: String,
  val romanji: String,
  val translations: List<String> = emptyList(),
  val explanation: String,
  val comments: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PhraseDto(
  val original: String,
  val hiragana: String,
  val romanji: String,
  val translation: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VocabularyDto(
  val original: String,
  val hiragana: String,
  val romanji: String,
  val meaning: String,
  val type: String,
)
