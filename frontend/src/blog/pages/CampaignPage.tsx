/**
 * 체험단 캠페인 목록 — 3플랫폼 병합, 필터·정렬, 페이징, 새로고침.
 * 카드에서「리뷰 작성」→ /blog?storeName=... 이동.
 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { CAMPAIGN_PAGE_SIZE, fetchCacheAge, fetchCampaignRefreshLogs, fetchCampaignRefreshPreview, fetchCampaignRefreshStatus, fetchCampaigns, startCampaignRefresh, type CampaignCard, type CampaignFetchLogEntry } from '../api/client';
import { useRegion } from '../../shared/RegionContext';

/** 플랫폼별 카드 색상 클래스 */
const PLATFORM_CLASS: Record<string, string> = {
  디너의여왕: 'dq',
  가보자: 'gb',
  강남맛집: 'gm',
};

/** 썸네일 없을 때 플레이스홀더 라벨 */
function thumbLabel(card: CampaignCard): string {
  const text = `${card.title} ${card.storeName}`;
  return text.includes('카페') ? 'CAFE' : 'FOOD';
}

/** 마감 임박(D-day) 뱃지 표시 여부 */
function isUrgent(deadline: string): boolean {
  if (!deadline) return false;
  return ['오늘', 'D-1', 'D-2', '1일', '2일'].some((w) => deadline.includes(w));
}

type PaginationBarProps = {
  total: number;
  page: number;
  totalPages: number;
  pageNumbers: number[];
  loading: boolean;
  setPage: (value: number | ((prev: number) => number)) => void;
  label: string;
  className?: string;
};

function PaginationBar({
  total,
  page,
  totalPages,
  pageNumbers,
  loading,
  setPage,
  label,
  className = '',
}: PaginationBarProps) {
  return (
    <nav className={`pagination-bar ${className}`.trim()} aria-label={label}>
      <span className="pagination-total">총 <strong>{total}</strong>건</span>
      <span className="pagination-divider" aria-hidden="true" />
      <span className="pagination-current"><strong>{page}</strong> / {totalPages}</span>
      {totalPages > 1 && (
        <>
          <span className="pagination-gap" aria-hidden="true" />
          <button
            type="button"
            className="page-btn page-nav"
            disabled={loading || page <= 1}
            onClick={() => setPage(1)}
            aria-label="첫 페이지"
          >
            «
          </button>
          <button
            type="button"
            className="page-btn page-nav"
            disabled={loading || page <= 1}
            onClick={() => setPage((p) => Math.max(1, p - 1))}
            aria-label="이전 페이지"
          >
            ‹
          </button>
          {pageNumbers.map((num) => (
            <button
              key={num}
              type="button"
              className={`page-btn page-num${num === page ? ' active' : ''}`}
              disabled={loading}
              onClick={() => setPage(num)}
              aria-current={num === page ? 'page' : undefined}
            >
              {num}
            </button>
          ))}
          <button
            type="button"
            className="page-btn page-nav"
            disabled={loading || page >= totalPages}
            onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
            aria-label="다음 페이지"
          >
            ›
          </button>
          <button
            type="button"
            className="page-btn page-nav"
            disabled={loading || page >= totalPages}
            onClick={() => setPage(totalPages)}
            aria-label="마지막 페이지"
          >
            »
          </button>
        </>
      )}
    </nav>
  );
}

/** 상단 eyebrow — 전체 선택 시 전국, 그 외 선택 지역 표시 */
function heroEyebrow(region: string): string {
  if (!region.trim() || region === '전체') {
    return '전국 · 체험단';
  }
  return `${region} · 체험단`;
}

export default function CampaignPage() {
  const { region, regions, setRegion, loadRegions } = useRegion();
  const [cards, setCards] = useState<CampaignCard[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [cacheAge, setCacheAge] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [refreshMessage, setRefreshMessage] = useState('');
  const [previewTotal, setPreviewTotal] = useState(0);
  const [showLogs, setShowLogs] = useState(false);
  const [fetchLogs, setFetchLogs] = useState<CampaignFetchLogEntry[]>([]);
  const logEndRef = useRef<HTMLDivElement>(null);
  const lastLogIdRef = useRef(0);
  const pollTimerRef = useRef<number | null>(null);
  const [platform, setPlatform] = useState('전체');
  const [sortBy, setSortBy] = useState('deadline');
  const navigate = useNavigate();

  const totalPages = useMemo(
    () => Math.max(1, Math.ceil(total / CAMPAIGN_PAGE_SIZE)),
    [total],
  );

  const filtersRef = useRef({ sortBy, platform, region });

  const load = useCallback(async (targetPage = 1) => {
    setError('');
    setLoading(true);
    try {
      const result = await fetchCampaigns(
        false,
        sortBy,
        platform,
        region,
        targetPage,
        CAMPAIGN_PAGE_SIZE,
      );
      setCards(result.items);
      setTotal(result.total);
      setPage(result.page);
      setCacheAge(await fetchCacheAge());
    } catch (e) {
      setError(e instanceof Error ? e.message : '조회 실패');
    } finally {
      setLoading(false);
    }
  }, [sortBy, platform, region]);

  const appendLogs = useCallback((entries: CampaignFetchLogEntry[]) => {
    if (entries.length === 0) {
      return;
    }
    lastLogIdRef.current = entries[entries.length - 1].id;
    setFetchLogs((prev) => [...prev, ...entries]);
  }, []);

  const loadPreview = useCallback(async (targetPage = 1) => {
    try {
      const result = await fetchCampaignRefreshPreview(
        sortBy,
        platform,
        region,
        targetPage,
        CAMPAIGN_PAGE_SIZE,
      );
      if (!result.partial) {
        return;
      }
      setCards(result.items);
      setTotal(result.total);
      setPreviewTotal(result.total);
      setPage(result.page);
    } catch (e) {
      setError(e instanceof Error ? e.message : '수집 미리보기 조회 실패');
    }
  }, [sortBy, platform, region]);

  const pollRefresh = useCallback(async () => {
    try {
      const previewPage = 1;
      const [status, logs, preview] = await Promise.all([
        fetchCampaignRefreshStatus(),
        fetchCampaignRefreshLogs(lastLogIdRef.current),
        fetchCampaignRefreshPreview(sortBy, platform, region, previewPage, CAMPAIGN_PAGE_SIZE),
      ]);
      appendLogs(logs);
      setRefreshMessage(status.message);
      if (preview.partial) {
        setCards(preview.items);
        setTotal(preview.total);
        setPreviewTotal(status.totalFetched || preview.total);
        setPage(preview.page);
      }
      if (status.status === 'running') {
        return;
      }
      setRefreshing(false);
      setPreviewTotal(0);
      if (pollTimerRef.current != null) {
        window.clearInterval(pollTimerRef.current);
        pollTimerRef.current = null;
      }
      await loadRegions(false);
      await load(page);
    } catch (e) {
      setRefreshing(false);
      setPreviewTotal(0);
      if (pollTimerRef.current != null) {
        window.clearInterval(pollTimerRef.current);
        pollTimerRef.current = null;
      }
      setError(e instanceof Error ? e.message : '수집 상태 조회 실패');
    }
  }, [appendLogs, load, loadRegions, page, platform, region, sortBy]);

  const startRefresh = useCallback(async () => {
    if (refreshing) {
      setShowLogs(true);
      return;
    }
    setError('');
    setShowLogs(true);
    setFetchLogs([]);
    lastLogIdRef.current = 0;
    setRefreshing(true);
    setPreviewTotal(0);
    setPage(1);
    setRefreshMessage('수집 준비 중…');
    try {
      const result = await startCampaignRefresh();
      setRefreshMessage(result.status.message);
      if (!result.started && result.status.status === 'running') {
        appendLogs(await fetchCampaignRefreshLogs(lastLogIdRef.current));
      }
      if (pollTimerRef.current != null) {
        window.clearInterval(pollTimerRef.current);
      }
      pollTimerRef.current = window.setInterval(() => {
        void pollRefreshRef.current();
      }, 1500);
      void pollRefresh();
    } catch (e) {
      setRefreshing(false);
      setError(e instanceof Error ? e.message : '수집 시작 실패');
    }
  }, [appendLogs, pollRefresh, refreshing]);

  const pollRefreshRef = useRef(pollRefresh);
  pollRefreshRef.current = pollRefresh;

  useEffect(() => {
    void (async () => {
      try {
        const status = await fetchCampaignRefreshStatus();
        if (status.status === 'running') {
          setRefreshing(true);
          setRefreshMessage(status.message);
          setShowLogs(true);
          const logs = await fetchCampaignRefreshLogs(0);
          appendLogs(logs);
          if (pollTimerRef.current != null) {
            window.clearInterval(pollTimerRef.current);
          }
          pollTimerRef.current = window.setInterval(() => {
            void pollRefreshRef.current();
          }, 1500);
        }
      } catch {
        // ignore initial status check failure
      }
    })();
    return () => {
      if (pollTimerRef.current != null) {
        window.clearInterval(pollTimerRef.current);
        pollTimerRef.current = null;
      }
    };
  }, [appendLogs]);

  useEffect(() => {
    if (showLogs) {
      logEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [fetchLogs, showLogs]);

  useEffect(() => {
    const prev = filtersRef.current;
    const filtersChanged = prev.sortBy !== sortBy
      || prev.platform !== platform
      || prev.region !== region;
    filtersRef.current = { sortBy, platform, region };
    const targetPage = refreshing ? 1 : (filtersChanged ? 1 : page);
    if (filtersChanged && page !== 1) {
      setPage(1);
      return;
    }
    if (refreshing) {
      void loadPreview(targetPage);
      return;
    }
    void load(targetPage);
  }, [sortBy, platform, region, page, load, loadPreview, refreshing]);

  const goBlog = (card: CampaignCard) => {
    const cardRegion = card.region || (region === '전체' ? '' : region);
    const params = new URLSearchParams({
      storeName: card.storeName,
      region: cardRegion || card.region || '',
      postType: '협찬',
      platform: card.platform,
      campaignId: card.campaignId,
    });
    navigate(`/blog?${params}`);
  };

  const pageNumbers = useMemo(() => {
    const maxButtons = 5;
    let start = Math.max(1, page - Math.floor(maxButtons / 2));
    const end = Math.min(totalPages, start + maxButtons - 1);
    start = Math.max(1, end - maxButtons + 1);
    const nums: number[] = [];
    for (let i = start; i <= end; i += 1) {
      nums.push(i);
    }
    return nums;
  }, [page, totalPages]);

  return (
    <section>
      <div className="page-hero">
        <div className="hero-eyebrow">{heroEyebrow(region)}</div>
        <h1>체험단</h1>
        <p className="hero-sub">3개 플랫폼의 전국 체험단을 한곳에서 탐색하고, 바로 리뷰 작성으로 이어갑니다.</p>
      </div>

      <div className="toolbar">
        <button
          type="button"
          className={`btn-refresh${refreshing ? ' is-loading' : ''}`}
          onClick={() => void startRefresh()}
          disabled={refreshing}
        >
          {refreshing && <span className="btn-spinner" aria-hidden="true" />}
          {refreshing ? '수집 중…' : '새로고침'}
        </button>
        <button
          type="button"
          className={`btn-icon${showLogs ? ' active' : ''}${refreshing ? ' pulse' : ''}`}
          onClick={() => setShowLogs((v) => !v)}
          title="로그 보기"
          aria-label="수집 로그 보기"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M4 6h16M4 12h10M4 18h14" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
          </svg>
        </button>
        {refreshing && (
          <span className="stat-pill refresh-status">
            {refreshMessage || '수집 중'}
            {previewTotal > 0 ? ` · ${previewTotal}건` : ''}
          </span>
        )}
        <span className="stat-pill">{cacheAge}</span>
        <span className={`stat-pill accent${refreshing ? ' preview' : ''}`}>
          {refreshing && previewTotal > 0 ? `${previewTotal}건 (수집 중)` : `${total}건`}
        </span>
      </div>

      {showLogs && (
        <div className="fetch-log-panel" role="log" aria-live="polite" aria-label="수집 로그">
          <div className="fetch-log-header">
            <strong>수집 로그</strong>
            {refreshing && <span className="fetch-log-live">실시간</span>}
            <button type="button" className="fetch-log-close" onClick={() => setShowLogs(false)} aria-label="로그 닫기">
              ×
            </button>
          </div>
          <div className="fetch-log-body">
            {fetchLogs.length === 0 ? (
              <p className="muted fetch-log-empty">아직 로그가 없습니다.</p>
            ) : (
              fetchLogs.map((entry) => (
                <div key={entry.id} className={`fetch-log-line level-${entry.level.toLowerCase()}`}>
                  <span className="fetch-log-time">{entry.time}</span>
                  <span className="fetch-log-msg">{entry.message}</span>
                </div>
              ))
            )}
            <div ref={logEndRef} />
          </div>
        </div>
      )}

      <div className="filter-row">
        <label>
          지역
          <select
            value={region}
            onChange={(e) => setRegion(e.target.value)}
            disabled={regions.length === 0}
          >
            {regions.length === 0 ? (
              <option value={region}>{region || '불러오는 중...'}</option>
            ) : (
              regions.map((v) => (
                <option key={v} value={v}>{v}</option>
              ))
            )}
          </select>
        </label>
        <label>
          플랫폼
          <select value={platform} onChange={(e) => setPlatform(e.target.value)}>
            {['전체', '디너의여왕', '가보자', '강남맛집'].map((v) => (
              <option key={v} value={v}>{v}</option>
            ))}
          </select>
        </label>
        <label>
          정렬
          <select value={sortBy} onChange={(e) => setSortBy(e.target.value)}>
            <option value="deadline">마감 임박순</option>
            <option value="competition">경쟁률 낮은순</option>
            <option value="recruit">모집 많은순</option>
          </select>
        </label>
      </div>

      {error && <p className="error">{error}</p>}
      {!loading && !refreshing && cards.length === 0 && (
        <p className="muted">조건에 맞는 체험단이 없습니다. 새로고침을 눌러 다시 시도해 보세요.</p>
      )}
      {refreshing && cards.length === 0 && (
        <p className="muted">수집을 시작했습니다. 잠시 후 카드가 표시됩니다.</p>
      )}

      {total > 0 && !refreshing && (
        <PaginationBar
          total={total}
          page={page}
          totalPages={totalPages}
          pageNumbers={pageNumbers}
          loading={loading}
          setPage={setPage}
          label="체험단 페이지"
        />
      )}

      {refreshing && cards.length > 0 && (
        <p className="preview-hint muted">플랫폼별로 수집되는 대로 목록이 갱신됩니다.</p>
      )}

      <div className="card-grid">
        {cards.map((card) => (
          <article className="campaign-card" key={`${card.platform}-${card.campaignId}`}>
            <div className="campaign-thumb-wrap">
              {card.deadline && (
                <span className={`deadline-badge${isUrgent(card.deadline) ? ' urgent' : ''}`}>
                  {card.deadline}
                </span>
              )}
              {card.thumbnailUrl ? (
                <img className="campaign-thumb" src={card.thumbnailUrl} alt={card.storeName} />
              ) : (
                <div className="campaign-thumb-placeholder">
                  {thumbLabel(card)}
                </div>
              )}
            </div>
            <div className="campaign-body">
              <div className="campaign-header-row">
                <span className={`platform-badge ${PLATFORM_CLASS[card.platform] ?? 'gm'}`}>
                  {card.platform}
                </span>
              </div>
              <h3 className="campaign-title">{card.storeName || card.title}</h3>
              {card.benefit && <div className="campaign-benefit">{card.benefit}</div>}
              <div className="campaign-stats">
                <div className="stat-box">
                  <div className="label">신청 / 모집</div>
                  <div className="value">{card.applied || '-'} / {card.recruit || '-'}</div>
                </div>
                <div className="stat-box">
                  <div className="label">경쟁률</div>
                  <div className={`value${card.competition >= 10 ? ' hot' : ''}`}>
                    {card.competition}배
                  </div>
                </div>
              </div>
              {(card.district || card.region) && (
                <span className="meta-chip">
                  {card.region}{card.district ? ` ${card.district}` : ''}
                </span>
              )}
              <div className="card-actions">
                <button onClick={() => goBlog(card)}>리뷰 작성</button>
                <a className="btn secondary" href={card.detailUrl} target="_blank" rel="noreferrer">상세</a>
              </div>
            </div>
          </article>
        ))}
      </div>

      {totalPages > 1 && !refreshing && (
        <PaginationBar
          total={total}
          page={page}
          totalPages={totalPages}
          pageNumbers={pageNumbers}
          loading={loading}
          setPage={setPage}
          label="체험단 페이지 하단"
          className="pagination-bar-bottom"
        />
      )}
    </section>
  );
}
