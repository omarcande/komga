export interface GeminiAnalysisDto {
  panels: PanelDto[]
  vocabulary: VocabularyDto[]
  pageSummary?: string
  error?: string
}

export interface PanelDto {
  panelNumber: number
  description?: string
  context?: string
  dialogues: DialogueDto[]
}

export interface DialogueDto {
  speakerHint?: string
  original: string
  hiragana: string
  romanji: string
  translations: string[]
  explanation: string
  comments?: string
}

export interface VocabularyDto {
  original: string
  hiragana: string
  romanji: string
  meaning: string
  type: string
}
