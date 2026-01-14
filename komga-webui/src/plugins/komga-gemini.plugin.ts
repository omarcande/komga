import {AxiosInstance} from 'axios'
import _Vue from 'vue'
import KomgaGeminiService from '@/services/komga-gemini.service'

export default {
  install(
    Vue: typeof _Vue,
    {http}: { http: AxiosInstance }) {
    Vue.prototype.$komgaGemini = new KomgaGeminiService(http)
  },
}

declare module 'vue/types/vue' {
  interface Vue {
    $komgaGemini: KomgaGeminiService;
  }
}
