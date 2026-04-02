import axios from 'axios'
import type { DetectResponse } from './types'

/** Let the browser set multipart boundary; manual Content-Type breaks file uploads. */
const multipartConfig = {}

export type VerifyResponse = {
  rawCosine: number
  similarity: number
  threshold: number
  samePerson: boolean
}
export type VerifyCalibrationResponse = {
  sameSimilarity: number
  diffSimilarity: number
  suggestedThreshold: number
  note: string
}
export type FaceCheckResponse = {
  normalFace: boolean
  threshold: number
  requireSingleFace: boolean
  faceCount: number
  bestScore: number | null
  reason: string
}

export async function detectFace(file: File, params?: {
  returnLandmarks?: boolean
  returnCrops?: boolean
  cropMaxSize?: number
  scoreThreshold?: number
  nmsThreshold?: number
  topK?: number
}): Promise<DetectResponse> {
  const form = new FormData()
  form.append('file', file)
  if (params) {
    for (const [k, v] of Object.entries(params)) {
      if (v === undefined || v === null) continue
      form.append(k, String(v))
    }
  }
  const { data } = await axios.post<DetectResponse>('/api/face/detect', form, multipartConfig)
  return data
}

export async function verifyFace(file1: File, file2: File, threshold: number): Promise<VerifyResponse> {
  const form = new FormData()
  form.append('file1', file1)
  form.append('file2', file2)
  form.append('threshold', String(threshold))
  const { data } = await axios.post<VerifyResponse>('/api/face/verify', form, multipartConfig)
  return data
}

export async function checkFace(file: File, threshold: number, requireSingleFace: boolean): Promise<FaceCheckResponse> {
  const form = new FormData()
  form.append('file', file)
  form.append('threshold', String(threshold))
  form.append('requireSingleFace', String(requireSingleFace))
  const { data } = await axios.post<FaceCheckResponse>('/api/face/check', form, multipartConfig)
  return data
}

export async function calibrateVerifyThreshold(
  same1: File,
  same2: File,
  diff1: File,
  diff2: File,
): Promise<VerifyCalibrationResponse> {
  const form = new FormData()
  form.append('same1', same1)
  form.append('same2', same2)
  form.append('diff1', diff1)
  form.append('diff2', diff2)
  const { data } = await axios.post<VerifyCalibrationResponse>(
    '/api/face/verify/calibrate',
    form,
    multipartConfig,
  )
  return data
}

/** Human-readable message from axios / Spring Boot error payloads */
export function formatApiError(e: unknown): string {
  if (axios.isAxiosError(e)) {
    const d = e.response?.data
    if (typeof d === 'string' && d.trim()) return d
    if (d && typeof d === 'object') {
      const o = d as Record<string, unknown>
      if (typeof o.message === 'string') return o.message
      if (typeof o.error === 'string') return o.error
      if (Array.isArray(o.errors)) return String(o.errors[0])
    }
    if (e.response?.status) return `HTTP ${e.response.status} ${e.response.statusText || ''}`.trim()
  }
  if (e instanceof Error) return e.message
  return String(e)
}

