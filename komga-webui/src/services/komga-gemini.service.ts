import {AxiosInstance} from 'axios'
import {GeminiAnalysisDto} from '@/types/komga-gemini'

const API_BOOKS = '/api/v1/books'

export default class KomgaGeminiService {
  private http: AxiosInstance
  private abortController: AbortController | null = null

  constructor(http: AxiosInstance) {
    this.http = http
  }

  async analyzeBookPage(bookId: string, pageNumber: number, refresh: boolean = false): Promise<GeminiAnalysisDto> {
    // Cancel any in-flight request
    this.cancelPendingRequest()

    // Create a new AbortController for this request
    this.abortController = new AbortController()

    try {
      const params = refresh ? {refresh: true} : {}
      const response = await this.http.post(
        `${API_BOOKS}/${bookId}/pages/${pageNumber}/analyze`,
        null,
        {
          params,
          signal: this.abortController.signal,
        },
      )
      return response.data
    } catch (e: any) {
      if (e.name === 'CanceledError' || e.code === 'ERR_CANCELED') {
        throw new Error('REQUEST_CANCELLED')
      }
      if (e.response?.status === 503) {
        throw new Error('Gemini analysis is not configured on the server')
      }
      let msg = 'An error occurred while analyzing the page'
      if (e.response?.data?.message) {
        msg += `: ${e.response.data.message}`
      }
      throw new Error(msg)
    } finally {
      this.abortController = null
    }
  }

  cancelPendingRequest(): void {
    if (this.abortController) {
      this.abortController.abort()
      this.abortController = null
    }
  }
}
