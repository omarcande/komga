<template>
  <v-dialog v-model="input" scrollable max-width="800" @keydown.esc.stop="">
    <v-card :max-height="$vuetify.breakpoint.height * 0.9" dark>
      <v-toolbar dark color="primary" dense>
        <v-btn icon dark @click="input = false">
          <v-icon>mdi-close</v-icon>
        </v-btn>
        <v-toolbar-title>{{ $t('bookreader.gemini_analysis') }}</v-toolbar-title>
        <v-spacer />
        <v-btn icon @click="refresh" :disabled="loading">
          <v-icon>mdi-refresh</v-icon>
        </v-btn>
      </v-toolbar>

      <v-card-text class="pa-4">
        <!-- Loading State -->
        <div v-if="loading" class="text-center pa-8">
          <v-progress-circular indeterminate color="primary" size="64" />
          <p class="mt-4">{{ $t('bookreader.analyzing_page') }}</p>
        </div>

        <!-- Error State -->
        <v-alert v-else-if="error" type="error" text>{{ error }}</v-alert>

        <!-- Results -->
        <div v-else-if="analysis">
          <!-- API Error from Gemini -->
          <v-alert v-if="analysis.error" type="warning" text class="mb-4">
            {{ analysis.error }}
          </v-alert>

          <!-- Page Summary -->
          <v-alert
            v-if="analysis.pageSummary"
            type="info"
            text
            dense
            class="mb-4"
          >
            <div class="text-subtitle-2 mb-1">{{ $t('bookreader.gemini.page_summary') }}</div>
            <div>{{ analysis.pageSummary }}</div>
          </v-alert>

          <!-- Panels Section -->
          <div v-if="analysis.panels && analysis.panels.length > 0">
            <div v-for="panel in analysis.panels" :key="'panel-' + panel.panelNumber" class="mb-4">
              <v-card outlined class="panel-card">
                <v-card-title class="py-2 px-4">
                  <v-icon small class="mr-2">mdi-image-frame</v-icon>
                  {{ $t('bookreader.gemini.panel') }} {{ panel.panelNumber }}
                </v-card-title>
                <v-card-text class="py-2 px-4">
                  <!-- Panel Description -->
                  <div v-if="panel.description" class="mb-2">
                    <span class="text-caption text--secondary">{{ $t('bookreader.gemini.scene') }}:</span>
                    <span class="ml-1">{{ panel.description }}</span>
                  </div>

                  <!-- Panel Context -->
                  <div v-if="panel.context" class="mb-3">
                    <span class="text-caption text--secondary">{{ $t('bookreader.gemini.panel_context') }}:</span>
                    <span class="ml-1 font-italic">{{ panel.context }}</span>
                  </div>

                  <!-- Dialogues -->
                  <div v-if="panel.dialogues && panel.dialogues.length > 0">
                    <v-expansion-panels accordion flat class="dialogue-panels">
                      <v-expansion-panel
                        v-for="(dialogue, di) in panel.dialogues"
                        :key="'d-' + panel.panelNumber + '-' + di"
                      >
                        <v-expansion-panel-header class="py-2">
                          <div>
                            <v-chip
                              v-if="dialogue.speakerHint"
                              x-small
                              :color="getSpeakerColor(dialogue.speakerHint)"
                              text-color="white"
                              class="mr-2"
                            >
                              {{ dialogue.speakerHint }}
                            </v-chip>
                            <span class="text-subtitle-1 japanese-text">{{ dialogue.original }}</span>
                            <div class="text-caption mt-1" v-if="dialogue.translations && dialogue.translations.length > 0">
                              {{ dialogue.translations[0] }}
                            </div>
                          </div>
                        </v-expansion-panel-header>
                        <v-expansion-panel-content>
                          <div class="mb-2">
                            <strong>{{ $t('bookreader.gemini.hiragana') }}:</strong>
                            <span class="japanese-text ml-2">{{ dialogue.hiragana }}</span>
                          </div>
                          <div class="mb-2">
                            <strong>{{ $t('bookreader.gemini.romanji') }}:</strong>
                            <span class="ml-2">{{ dialogue.romanji }}</span>
                          </div>
                          <div v-if="dialogue.translations && dialogue.translations.length > 1" class="mb-2">
                            <strong>{{ $t('bookreader.gemini.alt_translations') }}:</strong>
                            <ul class="ml-4">
                              <li v-for="(trans, ti) in dialogue.translations.slice(1)" :key="ti">{{ trans }}</li>
                            </ul>
                          </div>
                          <div v-if="dialogue.explanation" class="mb-2">
                            <strong>{{ $t('bookreader.gemini.explanation') }}:</strong>
                            <div class="explanation-content mt-1" v-html="renderMarkdown(dialogue.explanation)"></div>
                          </div>
                          <div v-if="dialogue.comments" class="mb-2">
                            <strong>{{ $t('bookreader.gemini.comments') }}:</strong>
                            <div class="mt-1">{{ dialogue.comments }}</div>
                          </div>
                        </v-expansion-panel-content>
                      </v-expansion-panel>
                    </v-expansion-panels>
                  </div>

                  <!-- No Dialogue -->
                  <div v-else class="text-caption text--secondary font-italic">
                    {{ $t('bookreader.gemini.no_dialogue') }}
                  </div>
                </v-card-text>
              </v-card>
            </div>
          </div>

          <!-- Vocabulary Section -->
          <div v-if="analysis.vocabulary && analysis.vocabulary.length > 0" class="mt-4">
            <v-divider class="mb-4" />
            <h3 class="text-h6 mb-2">{{ $t('bookreader.gemini.vocabulary') }}</h3>
            <v-expansion-panels accordion>
              <v-expansion-panel v-for="(vocab, i) in analysis.vocabulary" :key="'v-' + i">
                <v-expansion-panel-header>
                  <div class="d-flex align-center">
                    <span class="text-subtitle-1 japanese-text mr-2">{{ vocab.original }}</span>
                    <span class="text-caption mr-2">({{ vocab.hiragana }})</span>
                    <v-chip
                      x-small
                      :color="getVocabTypeColor(vocab.type)"
                      text-color="white"
                      class="mr-2"
                    >
                      {{ vocab.type }}
                    </v-chip>
                  </div>
                </v-expansion-panel-header>
                <v-expansion-panel-content>
                  <div class="mb-2">
                    <strong>{{ $t('bookreader.gemini.meaning') }}:</strong>
                    <span class="ml-2">{{ vocab.meaning }}</span>
                  </div>
                  <div class="mb-2">
                    <strong>{{ $t('bookreader.gemini.romanji') }}:</strong>
                    <span class="ml-2">{{ vocab.romanji }}</span>
                  </div>
                </v-expansion-panel-content>
              </v-expansion-panel>
            </v-expansion-panels>
          </div>

          <!-- No content -->
          <div v-if="!hasContent" class="text-center pa-4">
            <v-icon large class="mb-2">mdi-text-search</v-icon>
            <p>{{ $t('bookreader.gemini.no_content') }}</p>
          </div>
        </div>
      </v-card-text>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">
import Vue from 'vue'
import {marked} from 'marked'
import {GeminiAnalysisDto} from '@/types/komga-gemini'

// Dark colors that work well with white text
const SPEAKER_COLORS = [
  '#6B4C9A', // Purple
  '#2E7D32', // Green
  '#C62828', // Red
  '#1565C0', // Blue
  '#EF6C00', // Orange
  '#00838F', // Cyan
  '#AD1457', // Pink
  '#4527A0', // Deep Purple
  '#00695C', // Teal
  '#D84315', // Deep Orange
  '#5D4037', // Brown
  '#37474F', // Blue Grey
]

const VOCAB_TYPE_COLORS: Record<string, string> = {
  noun: '#1565C0',      // Blue
  verb: '#2E7D32',      // Green
  adjective: '#C62828', // Red
  adverb: '#6B4C9A',    // Purple
}

// Simple hash function for consistent color assignment
function hashString(str: string): number {
  let hash = 0
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i)
    hash = ((hash << 5) - hash) + char
    hash = hash & hash // Convert to 32-bit integer
  }
  return Math.abs(hash)
}

export default Vue.extend({
  name: 'GeminiAnalysisDialog',
  props: {
    value: {
      type: Boolean,
      required: true,
    },
    bookId: {
      type: String,
      required: true,
    },
    pageNumber: {
      type: Number,
      required: true,
    },
  },
  data: () => ({
    loading: false,
    error: null as string | null,
    analysis: null as GeminiAnalysisDto | null,
    // Cache analysis results per page for instant navigation
    pageCache: {} as Record<string, GeminiAnalysisDto>,
    // Track speaker colors for consistency within a session
    speakerColorMap: {} as Record<string, string>,
    nextColorIndex: 0,
  }),
  computed: {
    input: {
      get(): boolean {
        return this.value
      },
      set(val: boolean) {
        this.$emit('input', val)
      },
    },
    hasContent(): boolean {
      if (!this.analysis) return false
      return (
        (this.analysis.panels?.length ?? 0) > 0 ||
        (this.analysis.vocabulary?.length ?? 0) > 0
      )
    },
    cacheKey(): string {
      return `${this.bookId}-${this.pageNumber}`
    },
  },
  watch: {
    value(val) {
      if (val) {
        this.loadAnalysis()
      } else {
        // Dialog is closing, cancel any pending request
        this.$komgaGemini.cancelPendingRequest()
      }
    },
    pageNumber() {
      if (this.value) {
        this.loadAnalysis()
      }
    },
    bookId() {
      // Clear cache when switching books
      this.pageCache = {}
      this.speakerColorMap = {}
      this.nextColorIndex = 0
      if (this.value) {
        this.loadAnalysis()
      }
    },
  },
  methods: {
    loadAnalysis() {
      // Check if we have a cached result for this page
      const cached = this.pageCache[this.cacheKey]
      if (cached) {
        this.analysis = cached
        this.error = null
        return
      }
      // No cache, fetch from API
      this.fetchAnalysis()
    },
    async fetchAnalysis(refresh: boolean = false) {
      this.loading = true
      this.error = null

      if (refresh) {
        // Remove from local cache on refresh
        delete this.pageCache[this.cacheKey]
      }

      try {
        const result = await this.$komgaGemini.analyzeBookPage(this.bookId, this.pageNumber, refresh)
        this.analysis = result
        // Store in local cache
        this.pageCache[this.cacheKey] = result
      } catch (e: any) {
        // Ignore cancelled requests - they're intentional
        if (e.message === 'REQUEST_CANCELLED') {
          return
        }
        this.error = e.message
      } finally {
        this.loading = false
      }
    },
    refresh() {
      this.analysis = null
      this.fetchAnalysis(true)
    },
    renderMarkdown(text: string): string {
      if (!text) return ''
      return marked.parse(text) as string
    },
    getSpeakerColor(speaker: string): string {
      if (!speaker) return SPEAKER_COLORS[0]

      // Check if we already assigned a color to this speaker
      if (this.speakerColorMap[speaker]) {
        return this.speakerColorMap[speaker]
      }

      // Use hash-based color for consistency
      const hash = hashString(speaker)
      const color = SPEAKER_COLORS[hash % SPEAKER_COLORS.length]
      this.speakerColorMap[speaker] = color
      return color
    },
    getVocabTypeColor(type: string): string {
      const normalizedType = type?.toLowerCase() || ''
      return VOCAB_TYPE_COLORS[normalizedType] || '#455A64' // Default to blue-grey
    },
  },
})
</script>

<style scoped>
.japanese-text {
  font-family: 'Noto Sans JP', 'Hiragino Sans', 'Yu Gothic', sans-serif;
}

.explanation-content :deep(ul) {
  padding-left: 20px;
}

.explanation-content :deep(li) {
  margin-bottom: 4px;
}

.explanation-content :deep(strong) {
  color: #90caf9;
}

.panel-card {
  border-color: rgba(255, 255, 255, 0.12);
}

.dialogue-panels {
  background: transparent;
}

.dialogue-panels :deep(.v-expansion-panel) {
  background: rgba(255, 255, 255, 0.05);
}

.dialogue-panels :deep(.v-expansion-panel::before) {
  box-shadow: none;
}
</style>
