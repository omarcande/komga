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

          <!-- Sentences Section -->
          <div v-if="analysis.sentences && analysis.sentences.length > 0">
            <h3 class="text-h6 mb-2">{{ $t('bookreader.gemini.sentences') }}</h3>
            <v-expansion-panels accordion class="mb-4">
              <v-expansion-panel v-for="(sentence, i) in analysis.sentences" :key="'s-' + i">
                <v-expansion-panel-header>
                  <div>
                    <div class="text-subtitle-1 japanese-text">{{ sentence.original }}</div>
                    <div class="text-caption" v-if="sentence.translations && sentence.translations.length > 0">
                      {{ sentence.translations[0] }}
                    </div>
                  </div>
                </v-expansion-panel-header>
                <v-expansion-panel-content>
                  <div class="mb-2">
                    <strong>{{ $t('bookreader.gemini.hiragana') }}:</strong>
                    <span class="japanese-text ml-2">{{ sentence.hiragana }}</span>
                  </div>
                  <div class="mb-2">
                    <strong>{{ $t('bookreader.gemini.romanji') }}:</strong>
                    <span class="ml-2">{{ sentence.romanji }}</span>
                  </div>
                  <div v-if="sentence.translations && sentence.translations.length > 1" class="mb-2">
                    <strong>{{ $t('bookreader.gemini.alt_translations') }}:</strong>
                    <ul class="ml-4">
                      <li v-for="(trans, ti) in sentence.translations.slice(1)" :key="ti">{{ trans }}</li>
                    </ul>
                  </div>
                  <div v-if="sentence.explanation" class="mb-2">
                    <strong>{{ $t('bookreader.gemini.explanation') }}:</strong>
                    <div class="explanation-content mt-1" v-html="renderMarkdown(sentence.explanation)"></div>
                  </div>
                  <div v-if="sentence.comments" class="mb-2">
                    <strong>{{ $t('bookreader.gemini.comments') }}:</strong>
                    <div class="mt-1">{{ sentence.comments }}</div>
                  </div>
                </v-expansion-panel-content>
              </v-expansion-panel>
            </v-expansion-panels>
          </div>

          <!-- Phrases Section -->
          <div v-if="analysis.phrases && analysis.phrases.length > 0">
            <h3 class="text-h6 mb-2">{{ $t('bookreader.gemini.phrases') }}</h3>
            <v-expansion-panels accordion class="mb-4">
              <v-expansion-panel v-for="(phrase, i) in analysis.phrases" :key="'p-' + i">
                <v-expansion-panel-header>
                  <div class="d-flex align-center">
                    <span class="text-subtitle-1 japanese-text mr-2">{{ phrase.original }}</span>
                    <span class="text-caption">- {{ phrase.translation }}</span>
                  </div>
                </v-expansion-panel-header>
                <v-expansion-panel-content>
                  <div class="mb-2">
                    <strong>{{ $t('bookreader.gemini.hiragana') }}:</strong>
                    <span class="japanese-text ml-2">{{ phrase.hiragana }}</span>
                  </div>
                  <div class="mb-2">
                    <strong>{{ $t('bookreader.gemini.romanji') }}:</strong>
                    <span class="ml-2">{{ phrase.romanji }}</span>
                  </div>
                </v-expansion-panel-content>
              </v-expansion-panel>
            </v-expansion-panels>
          </div>

          <!-- Vocabulary Section -->
          <div v-if="analysis.vocabulary && analysis.vocabulary.length > 0">
            <h3 class="text-h6 mb-2">{{ $t('bookreader.gemini.vocabulary') }}</h3>
            <v-expansion-panels accordion>
              <v-expansion-panel v-for="(vocab, i) in analysis.vocabulary" :key="'v-' + i">
                <v-expansion-panel-header>
                  <div class="d-flex align-center">
                    <span class="text-subtitle-1 japanese-text mr-2">{{ vocab.original }}</span>
                    <span class="text-caption mr-2">({{ vocab.hiragana }})</span>
                    <v-chip x-small color="primary" outlined class="mr-2">
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
    lastPageNumber: 0,
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
        (this.analysis.sentences?.length ?? 0) > 0 ||
        (this.analysis.phrases?.length ?? 0) > 0 ||
        (this.analysis.vocabulary?.length ?? 0) > 0
      )
    },
  },
  watch: {
    value(val) {
      if (val && (this.analysis === null || this.pageNumber !== this.lastPageNumber)) {
        this.fetchAnalysis()
      } else if (!val) {
        // Dialog is closing, cancel any pending request
        this.$komgaGemini.cancelPendingRequest()
      }
    },
    pageNumber(val) {
      if (this.value && val !== this.lastPageNumber) {
        this.analysis = null
        this.error = null
        this.fetchAnalysis()
      }
    },
  },
  methods: {
    async fetchAnalysis(refresh: boolean = false) {
      this.loading = true
      this.error = null
      this.lastPageNumber = this.pageNumber
      try {
        this.analysis = await this.$komgaGemini.analyzeBookPage(this.bookId, this.pageNumber, refresh)
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
</style>
