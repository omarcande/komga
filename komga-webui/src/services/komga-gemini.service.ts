import {AxiosInstance} from 'axios'
import {GeminiAnalysisDto} from '@/types/komga-gemini'

const API_BOOKS = '/api/v1/books'

export default class KomgaGeminiService {
  private http: AxiosInstance

  constructor(http: AxiosInstance) {
    this.http = http
  }

  async analyzeBookPage(bookId: string, pageNumber: number): Promise<GeminiAnalysisDto> {
    try {
      return (await this.http.post(`${API_BOOKS}/${bookId}/pages/${pageNumber}/analyze`)).data
    } catch (e: any) {
      if (e.response?.status === 503) {
        throw new Error('Gemini analysis is not configured on the server')
      }
      let msg = 'An error occurred while analyzing the page'
      if (e.response?.data?.message) {
        msg += `: ${e.response.data.message}`
      }
      throw new Error(msg)
    }
  }
}
