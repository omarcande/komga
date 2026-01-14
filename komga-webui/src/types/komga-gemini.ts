export interface GeminiAnalysisDto {
  sentences: SentenceDto[]
  phrases: PhraseDto[]
  vocabulary: VocabularyDto[]
  error?: string
}

export interface SentenceDto {
  original: string
  hiragana: string
  romanji: string
  translations: string[]
  explanation: string
  comments?: string
}

export interface PhraseDto {
  original: string
  hiragana: string
  romanji: string
  translation: string
}

export interface VocabularyDto {
  original: string
  hiragana: string
  romanji: string
  meaning: string
  type: string
}
