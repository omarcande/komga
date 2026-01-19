package org.gotson.komga.interfaces.api.rest.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class GeminiAnalysisDto(
  val panels: List<PanelDto> = emptyList(),
  val vocabulary: List<VocabularyDto> = emptyList(),
  val pageSummary: String? = null,
  val error: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PanelDto(
  val panelNumber: Int,
  val description: String? = null,
  val context: String? = null,
  val dialogues: List<DialogueDto> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DialogueDto(
  val speakerHint: String? = null,
  val original: String,
  val hiragana: String,
  val romanji: String,
  val translations: List<String> = emptyList(),
  val explanation: String,
  val comments: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VocabularyDto(
  val original: String,
  val hiragana: String,
  val romanji: String,
  val meaning: String,
  val type: String,
)
