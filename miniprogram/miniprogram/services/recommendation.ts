import type {
  RecommendationRequest,
  RecommendationResponse,
  RecommendationStatusResponse,
} from '../domain/recommendation-profile'
import {
  absolutePublicUrl,
  request,
} from './request'

export function getRecommendationStatus(): Promise<RecommendationStatusResponse> {
  return request<RecommendationStatusResponse>('/app/recommendations/status')
}

export async function requestRecommendation(
  payload: RecommendationRequest,
): Promise<RecommendationResponse> {
  const response = await request<RecommendationResponse>('/app/recommendations', {
    method: 'POST',
    data: payload,
  })
  return {
    ...response,
    items: response.items.map((item) => ({
      ...item,
      imageUrl: absolutePublicUrl(item.imageUrl) || '',
    })),
  }
}
