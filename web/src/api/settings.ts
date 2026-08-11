import api from './index'

export interface AiSettings {
  provider: string
  apiKey: string
  apiBase: string
  model: string
  temperature: number
  embeddingEnabled: boolean
  embeddingProvider: string
  embeddingModel: string
  advisorStyle: string
}

export interface ProviderInfo {
  key: string
  name: string
  description: string
  defaultModel: string
  defaultBaseUrl: string
  needsApiKey: boolean
}

export interface AiSettingsResponse extends AiSettings {
  providers: ProviderInfo[]
  embeddingActive: boolean
  embeddingProviders: ProviderInfo[]
}

export async function getAiSettings(): Promise<AiSettingsResponse> {
  const res = await api.get('/settings/ai')
  return res.data
}

export async function updateAiSettings(settings: AiSettings): Promise<AiSettingsResponse> {
  const res = await api.put('/settings/ai', settings)
  return res.data
}

export async function testAiConnection(settings: AiSettings): Promise<{ success: boolean; message: string }> {
  const res = await api.post('/settings/ai/test', settings)
  return res.data
}

// --- Prompt Templates ---

export interface PromptTemplateMeta {
  key: string
  name: string
  description: string
  isBuiltin: boolean
  content: string
}

export async function getPromptTemplates(): Promise<Record<string, PromptTemplateMeta>> {
  const res = await api.get('/settings/prompts')
  return res.data
}

export async function updatePromptTemplates(updates: Record<string, string>): Promise<Record<string, string>> {
  const res = await api.put('/settings/prompts', updates)
  return res.data
}

// --- OCR Status ---

export interface OcrStatus {
  tesseractAvailable: boolean
  fallbackEnabled: boolean
}

export async function getOcrStatus(): Promise<OcrStatus> {
  const res = await api.get('/settings/ocr-status')
  return res.data
}
