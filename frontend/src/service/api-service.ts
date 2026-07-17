import type { AxiosInstance, AxiosResponse } from 'axios'
import axios from 'axios'

class APIService {
  private readonly client: AxiosInstance

  constructor() {
    this.client = axios.create({
      baseURL: '/api',
      headers: {
        'Content-Type': 'application/json',
      },
    })
    this.client.interceptors.response.use(
      (response: AxiosResponse) => {
        console.info(`received response status: ${response.status}`)
        return response
      },
      (error: unknown) => {
        if (axios.isAxiosError(error)) {
          console.error(`API response error status: ${error.response?.status ?? 'unknown'}`)
        } else {
          console.error('API response error')
        }
        return Promise.reject(error)
      },
    )
  }

  public getAxiosInstance(): AxiosInstance {
    return this.client
  }
}

export default new APIService()
