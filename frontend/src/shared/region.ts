import type { CampaignCard } from '../blog/api/client';
import { isKnownRegion } from '../blog/api/client';

const STORAGE_KEY = 'studio-region';
const DEFAULT_REGION = '대전';
export const ALL_REGIONS_OPTION = '전체';

/** 체험단 카드에서 지역 목록 추출 — 중복 제거, 전체·대전 우선, 나머지 가나다순 */
export function uniqueRegions(cards: CampaignCard[]): string[] {
  const seen = new Set<string>();
  for (const card of cards) {
    const value = card.region?.trim();
    if (value && isKnownRegion(value)) {
      seen.add(value);
    }
  }
  const sorted = [...seen].sort((a, b) => a.localeCompare(b, 'ko'));
  const rest = sorted.filter((r) => r !== DEFAULT_REGION);
  if (sorted.includes(DEFAULT_REGION)) {
    return [ALL_REGIONS_OPTION, DEFAULT_REGION, ...rest];
  }
  return [ALL_REGIONS_OPTION, ...sorted];
}

export function loadStoredRegion(): string {
  return localStorage.getItem(STORAGE_KEY)?.trim() || DEFAULT_REGION;
}

export function saveRegion(region: string): void {
  if (region.trim()) {
    localStorage.setItem(STORAGE_KEY, region.trim());
  }
}

export function regionFromSearchParam(value: string | null): string | null {
  const trimmed = value?.trim();
  return trimmed || null;
}

export function pickValidRegion(region: string, regions: string[]): string {
  if (regions.length === 0) {
    return region || DEFAULT_REGION;
  }
  if (regions.includes(region)) {
    return region;
  }
  if (regions.includes(DEFAULT_REGION)) {
    return DEFAULT_REGION;
  }
  return regions[0];
}
