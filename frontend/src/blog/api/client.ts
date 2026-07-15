/**
 * 백엔드 REST API 호출 전용 모듈.
 * pages/ 컴포넌트는 fetch를 직접 쓰지 않고 이 파일의 함수만 사용.
 */
const API_BASE = import.meta.env.DEV ? '' : '';

export type CampaignCard = {
  platform: string;
  campaignId: string;
  title: string;
  storeName: string;
  region: string;
  district: string;
  channel: string;
  benefit: string;
  applied: number;
  recruit: number;
  deadline: string;
  thumbnailUrl: string;
  detailUrl: string;
  competition: number;
};

export type CampaignPageResult = {
  page: number;
  size: number;
  total: number;
  items: CampaignCard[];
  /** 수집 진행 중 미리보기 응답 */
  partial?: boolean;
};

export const CAMPAIGN_PAGE_SIZE = 20;

const KNOWN_REGIONS = new Set([
  '전국',
  '서울', '경기', '경기도', '인천', '부산', '대구', '광주', '대전', '울산', '세종', '세종시',
  '강원', '강원도', '강원특별자치도', '제주', '제주도', '제주특별자치도',
  '충북', '충남', '충청북도', '충청남도',
  '전북', '전남', '전라북도', '전라남도', '전북특별자치',
  '경북', '경남', '경상북도', '경상남도',
]);

export function isKnownRegion(region: string | undefined | null): boolean {
  const value = region?.trim();
  if (!value) {
    return false;
  }
  if (KNOWN_REGIONS.has(value)) {
    return true;
  }
  return value.endsWith('광역시')
    || value.endsWith('특별시')
    || value.endsWith('특별자치시')
    || value.endsWith('특별자치도')
    || (value.endsWith('도') && value.length <= 5);
}

/** 공공 API 매장 필드(BPLC_NM, ROAD_NM_ADDR 등) */
export type StoreItem = Record<string, string>;

/** 체험단 목록 — 필터·정렬·페이징은 서버에서 적용 */
export async function fetchCampaigns(
  refresh = false,
  sortBy = 'deadline',
  platform = '전체',
  region?: string,
  page = 1,
  size = CAMPAIGN_PAGE_SIZE,
): Promise<CampaignPageResult> {
  const params = new URLSearchParams({
    refresh: String(refresh),
    sortBy,
    platform,
    page: String(page),
    size: String(size),
  });
  if (region) {
    params.set('region', region);
  }
  const res = await fetch(`${API_BASE}/api/campaigns/sorted?${params}`);
  if (!res.ok) throw new Error('체험단 조회 실패');
  return res.json();
}

export async function fetchCampaignRegions(refresh = false): Promise<string[]> {
  const params = new URLSearchParams({ refresh: String(refresh) });
  const res = await fetch(`${API_BASE}/api/campaigns/regions?${params}`);
  if (!res.ok) throw new Error('지역 목록 조회 실패');
  return res.json();
}

export async function fetchCacheAge(): Promise<string> {
  const res = await fetch(`${API_BASE}/api/campaigns/cache-age`);
  const data = await res.json();
  return data.label ?? '';
}

export type CampaignRefreshStatus = {
  status: 'idle' | 'running' | 'completed' | 'failed';
  message: string;
  startedAt: string | null;
  finishedAt: string | null;
  totalFetched: number;
};

export type CampaignFetchLogEntry = {
  id: number;
  time: string;
  level: string;
  message: string;
};

export async function startCampaignRefresh(): Promise<{ started: boolean; status: CampaignRefreshStatus }> {
  const res = await fetch(`${API_BASE}/api/campaigns/refresh`, { method: 'POST' });
  if (!res.ok) throw new Error('수집 시작 실패');
  return res.json();
}

export async function fetchCampaignRefreshStatus(): Promise<CampaignRefreshStatus> {
  const res = await fetch(`${API_BASE}/api/campaigns/refresh/status`);
  if (!res.ok) throw new Error('수집 상태 조회 실패');
  return res.json();
}

export async function fetchCampaignRefreshLogs(after = 0): Promise<CampaignFetchLogEntry[]> {
  const params = new URLSearchParams({ after: String(after) });
  const res = await fetch(`${API_BASE}/api/campaigns/refresh/logs?${params}`);
  if (!res.ok) throw new Error('수집 로그 조회 실패');
  return res.json();
}

/** 수집 진행 중 — 플랫폼별로 누적된 카드 미리보기 */
export async function fetchCampaignRefreshPreview(
  sortBy = 'deadline',
  platform = '전체',
  region?: string,
  page = 1,
  size = CAMPAIGN_PAGE_SIZE,
): Promise<CampaignPageResult> {
  const params = new URLSearchParams({
    sortBy,
    platform,
    page: String(page),
    size: String(size),
  });
  if (region) {
    params.set('region', region);
  }
  const res = await fetch(`${API_BASE}/api/campaigns/refresh/preview?${params}`);
  if (!res.ok) throw new Error('수집 미리보기 조회 실패');
  return res.json();
}

/** 공공 API 매장 후보 검색 */
export async function searchStores(storeName: string, region: string): Promise<StoreItem[]> {
  const params = new URLSearchParams({ storeName, region });
  const res = await fetch(`${API_BASE}/api/stores/search?${params}`);
  if (!res.ok) throw new Error('매장 조회 실패');
  return res.json();
}

/** 선택 매장의 상호·주소·영업시간 등 텍스트 블록 */
export async function fetchStoreInfo(
  storeName: string,
  region: string,
  selected?: StoreItem,
) {
  const params = new URLSearchParams({ storeName, region });
  if (selected?.BPLC_NM) {
    params.set('bplcNm', selected.BPLC_NM);
    params.set('roadNmAddr', selected.ROAD_NM_ADDR ?? '');
    params.set('lotnoAddr', selected.LOTNO_ADDR ?? '');
  }
  const res = await fetch(`${API_BASE}/api/stores/info?${params}`);
  if (!res.ok) throw new Error('매장 정보 조회 실패');
  return res.json();
}

export async function fetchMapLink(storeName: string, region: string) {
  const params = new URLSearchParams({ storeName, region });
  const res = await fetch(`${API_BASE}/api/stores/map-link?${params}`);
  return res.json();
}

/** multipart FormData → Job ID. 사진 필드명 = external/interior/menu/product */
export async function startBlogJob(formData: FormData): Promise<string> {
  const res = await fetch(`${API_BASE}/api/blog/generate`, { method: 'POST', body: formData });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || '생성 실패');
  }
  const data = await res.json();
  return data.jobId;
}

/** 2초 간격 폴링 — COMPLETED 시 downloadUrl·jobId 포함 */
export async function pollBlogJob(jobId: string) {
  const res = await fetch(`${API_BASE}/api/blog/jobs/${jobId}`);
  return res.json();
}

export type NaverSessionStatus = {
  connected: boolean;
  naverId: string;
  message: string;
  savedAt: string | null;
};

export type NaverJobStatus = {
  jobId: string;
  type: string;
  status: string;
  message: string;
  blogJobId: string | null;
  resultUrl: string | null;
};

export type NaverLogEntry = {
  id: number;
  time: string;
  level: string;
  message: string;
};

export async function fetchNaverSession(): Promise<NaverSessionStatus> {
  const res = await fetch(`${API_BASE}/api/naver/session`);
  if (!res.ok) throw new Error('네이버 세션 조회 실패');
  return res.json();
}

export async function startNaverBrowserLogin(blogJobId?: string): Promise<{ started: boolean; jobId?: string; message?: string }> {
  const body = blogJobId ? { blogJobId } : {};
  const res = await fetch(`${API_BASE}/api/naver/login/browser`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error('네이버 로그인 창 열기 실패');
  return res.json();
}

export async function logoutNaver(): Promise<void> {
  const res = await fetch(`${API_BASE}/api/naver/logout`, { method: 'POST' });
  if (!res.ok) throw new Error('네이버 로그아웃 실패');
}

export async function startNaverPublish(blogJobId: string): Promise<{ started: boolean; jobId?: string; message?: string }> {
  const res = await fetch(`${API_BASE}/api/naver/publish`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ blogJobId }),
  });
  if (!res.ok) throw new Error('네이버 발행 시작 실패');
  return res.json();
}

export async function pollNaverJob(jobId: string): Promise<NaverJobStatus> {
  const res = await fetch(`${API_BASE}/api/naver/jobs/${jobId}`);
  if (!res.ok) throw new Error('네이버 Job 조회 실패');
  return res.json();
}

export async function fetchNaverJobLogs(jobId: string, after = 0): Promise<NaverLogEntry[]> {
  const params = new URLSearchParams({ after: String(after) });
  const res = await fetch(`${API_BASE}/api/naver/jobs/${jobId}/logs?${params}`);
  if (!res.ok) throw new Error('네이버 로그 조회 실패');
  return res.json();
}

export function storeAddress(item: StoreItem): string {
  return item.ROAD_NM_ADDR || item.LOTNO_ADDR || '정보없음';
}

export function storeLabel(item: StoreItem): string {
  return `${item.BPLC_NM ?? ''} · ${storeAddress(item)}`;
}

export type AuthResponse = {
  valid: boolean;
  allowedApps: string[];
  isAdmin: boolean;
};

export async function verifyAuthCode(code: string): Promise<AuthResponse> {
  const params = new URLSearchParams({ code });
  const res = await fetch(`${API_BASE}/api/auth/verify?${params}`, {
    method: 'POST',
  });
  if (!res.ok) throw new Error('인증 코드 확인 실패');
  return res.json();
}

export async function issueAuthCode(adminCode: string, newCode: string, allowedApps: string[]): Promise<{ success: boolean; message?: string }> {
  const res = await fetch(`${API_BASE}/api/auth/issue`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ adminCode, newCode, allowedApps }),
  });
  if (!res.ok) {
    const errorData = await res.json().catch(() => ({}));
    throw new Error(errorData.message || '코드 발급 실패');
  }
  return res.json();
}

export async function fetchAuthCodes(adminCode: string): Promise<Record<string, string[]>> {
  const params = new URLSearchParams({ adminCode });
  const res = await fetch(`${API_BASE}/api/auth/codes?${params}`);
  if (!res.ok) throw new Error('코드 목록 조회 실패');
  return res.json();
}

export async function deleteAuthCode(adminCode: string, codeToDelete: string): Promise<{ success: boolean }> {
  const params = new URLSearchParams({ adminCode });
  const res = await fetch(`${API_BASE}/api/auth/codes/${encodeURIComponent(codeToDelete)}?${params}`, {
    method: 'DELETE',
  });
  if (!res.ok) throw new Error('코드 삭제 실패');
  return res.json();
}
