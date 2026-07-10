import { request } from './http'

export type OptionStatus = 'ENABLED' | 'DISABLED'

export interface DiyRule {
  minWristMm: number
  maxWristMm: number
  snugAllowanceMm: number
  standardAllowanceMm: number
  looseAllowanceMm: number
  toleranceMm: number
  maxBeadCount: number
  configVersion: number
}

export interface CraftFeeRule {
  id: number
  minBeadCount: number
  maxBeadCount: number
  fee: number
  ruleVersion: number
}

export interface CordOption {
  id: number
  code: string
  name: string
  colorHex: string
  extraFee: number
  status: OptionStatus
  sortOrder: number
  optionVersion: number
}

export interface DiyConfiguration {
  rule: DiyRule
  craftFeeRules: CraftFeeRule[]
  cordOptions: CordOption[]
}

export type SaveDiyRulePayload = Omit<DiyRule, 'configVersion'>

export interface SaveCraftFeeRulePayload {
  minBeadCount: number
  maxBeadCount: number
  fee: number
}

export type SaveCordOptionPayload = Omit<
  CordOption,
  'id' | 'optionVersion'
>

export const getAdminDiyConfiguration = (): Promise<DiyConfiguration> =>
  request<DiyConfiguration>('/admin/diy/config')

export const saveDiyRule = (
  payload: SaveDiyRulePayload,
): Promise<DiyConfiguration> =>
  request<DiyConfiguration>('/admin/diy/config', {
    method: 'PUT',
    body: JSON.stringify(payload),
  })

export const saveCraftFeeRules = (
  rules: SaveCraftFeeRulePayload[],
): Promise<DiyConfiguration> =>
  request<DiyConfiguration>('/admin/diy/craft-fees', {
    method: 'PUT',
    body: JSON.stringify({ rules }),
  })

export const createCordOption = (
  payload: SaveCordOptionPayload,
): Promise<DiyConfiguration> =>
  request<DiyConfiguration>('/admin/diy/cord-options', {
    method: 'POST',
    body: JSON.stringify(payload),
  })

export const updateCordOption = (
  id: number,
  payload: SaveCordOptionPayload,
): Promise<DiyConfiguration> =>
  request<DiyConfiguration>(`/admin/diy/cord-options/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
