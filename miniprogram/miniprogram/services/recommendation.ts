/** 封装推荐生成、状态和问卷接口，页面通过统一请求层复用身份与错误处理。 */
import type {
  RecommendationProfileResponse,
  RecommendationRequest,
  RecommendationResponse,
  RecommendationStatusResponse,
} from '../domain/recommendation-profile'
import {
  absolutePublicUrl,
  request,
} from './request'

export function getRecommendationStatus(): Promise<RecommendationStatusResponse> {
  return request<RecommendationStatusResponse>('/public/recommendations/status')
}

export function getRecommendationProfile(
  birthDate: string,
): Promise<RecommendationProfileResponse> {
  return request<RecommendationProfileResponse>(
    `/public/recommendations/profile?birthDate=${encodeURIComponent(birthDate)}`,
  )
}

export async function requestRecommendation(
  payload: RecommendationRequest,
): Promise<RecommendationResponse> {
  const response = await request<RecommendationResponse>('/app/recommendations', {
    method: 'POST',
    data: payload,
    timeout: 70000,
  })
  return {
    ...response,
    plans: response.plans.map((plan) => ({
      ...plan,
      items: plan.items.map((item) => ({
        ...item,
        imageUrl: absolutePublicUrl(item.imageUrl) || '',
      })),
    })),
  }
}
