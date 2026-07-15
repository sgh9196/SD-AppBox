/**
 * 블로그 원고 작성 페이지.
 * 매장 조회 → 사진 업로드 → 평점·유형 선택 → Gemini Job → preview.docx 다운로드.
 * 체험단에서 ?storeName=... 로 진입 시 쿼리 파라미터 자동 반영.
 */
import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  fetchMapLink,
  fetchNaverJobLogs,
  fetchNaverSession,
  fetchStoreInfo,
  logoutNaver,
  pollBlogJob,
  pollNaverJob,
  searchStores,
  startBlogJob,
  startNaverBrowserLogin,
  startNaverPublish,
  storeLabel,
  type NaverLogEntry,
  type NaverSessionStatus,
  type StoreItem,
} from '../api/client';
import { useRegion } from '../../shared/RegionContext';
import {
  groupFolderImages,
  PHOTO_CATEGORIES,
  summarizeFolderUpload,
  type PhotoCategory,
} from '../utils/photoFolderUpload';

const CATEGORIES = PHOTO_CATEGORIES;
const RATING_OPTIONS = [1, 2, 3, 4, 5] as const;
/** 사진 업로드 카드 UI 메타 — 마커 카테고리와 1:1 대응 */
const PHOTO_META: Record<string, { label: string; icon: string; hint: string }> = {
  external: { label: '외관', icon: '🏠', hint: '간판·입구·외부 전경' },
  interior: { label: '내부', icon: '🪑', hint: '좌석·인테리어·분위기' },
  menu: { label: '메뉴판', icon: '📋', hint: '메뉴판·가격표' },
  product: { label: '음식', icon: '🍽️', hint: '음식·음료·상품' },
};

export default function BlogPage() {
  const [params] = useSearchParams();
  const { region, regions, setRegion } = useRegion();
  const [storeName, setStoreName] = useState(params.get('storeName') ?? '');
  const [postType, setPostType] = useState(params.get('postType') ?? '협찬');
  const [bloggerName, setBloggerName] = useState('글로벌');
  const [rating, setRating] = useState(5);
  const [infoText, setInfoText] = useState('');
  const [link, setLink] = useState('');
  const [campaignGuideline, setCampaignGuideline] = useState('');
  const [files, setFiles] = useState<Record<string, File[]>>({});
  const [previews, setPreviews] = useState<Record<string, string[]>>({});
  const [status, setStatus] = useState('');
  const [error, setError] = useState('');
  const [candidates, setCandidates] = useState<StoreItem[]>([]);
  const [pickIndex, setPickIndex] = useState(0);
  const [loadingStore, setLoadingStore] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [storeSearched, setStoreSearched] = useState(false);
  const [blogJobId, setBlogJobId] = useState('');
  const [downloadUrl, setDownloadUrl] = useState('');
  const [naverSession, setNaverSession] = useState<NaverSessionStatus | null>(null);
  const [naverBrowserBusy, setNaverBrowserBusy] = useState(false);
  const [naverPublishing, setNaverPublishing] = useState(false);
  const [naverLogs, setNaverLogs] = useState<NaverLogEntry[]>([]);
  const [showNaverLogs, setShowNaverLogs] = useState(false);
  const [naverStatus, setNaverStatus] = useState('');
  const [photoUploadStatus, setPhotoUploadStatus] = useState('');
  const fileInputRefs = useRef<Record<string, HTMLInputElement | null>>({});
  const folderInputRef = useRef<HTMLInputElement | null>(null);
  const naverLogEndRef = useRef<HTMLDivElement>(null);
  const lastNaverLogIdRef = useRef(0);
  const naverPollRef = useRef<number | null>(null);

  // 미리보기 blob URL 정리
  useEffect(() => {
    return () => {
      Object.values(previews).flat().forEach((url) => URL.revokeObjectURL(url));
    };
  }, [previews]);

  /** 공공 API + 네이버로 매장 정보·지도 링크 조회 */
  const loadStoreInfo = async () => {
    if (!storeName.trim()) {
      setError('매장명을 입력하세요.');
      return;
    }
    setLoadingStore(true);
    setError('');
    setStoreSearched(false);
    try {
      const found = await searchStores(storeName.trim(), region.trim());
      setCandidates(found);
      setPickIndex(0);
      if (found.length === 0) {
        setInfoText('');
        setLink('');
        setError('검색 결과가 없습니다. 매장명을 바꿔 다시 조회해 보세요.');
        return;
      }
      const chosen = found[0];
      const info = await fetchStoreInfo(storeName.trim(), region.trim(), chosen);
      setInfoText(info.infoText ?? '');
      const map = await fetchMapLink(storeName.trim(), region.trim());
      setLink(map.url ?? '');
      setStoreSearched(true);
    } catch (e) {
      setError(e instanceof Error ? e.message : '매장 조회 실패');
    } finally {
      setLoadingStore(false);
    }
  };

  const onPickChange = async (index: number) => {
    setPickIndex(index);
    const chosen = candidates[index];
    if (!chosen) return;
    try {
      const info = await fetchStoreInfo(storeName.trim(), region.trim(), chosen);
      setInfoText(info.infoText ?? '');
    } catch (e) {
      setError(e instanceof Error ? e.message : '매장 정보 조회 실패');
    }
  };

  const appendFilesToCategory = (cat: PhotoCategory, incoming: File[]) => {
    if (incoming.length === 0) return;
    setFiles((prev) => ({
      ...prev,
      [cat]: [...(prev[cat] ?? []), ...incoming],
    }));
    setPreviews((prev) => ({
      ...prev,
      [cat]: [...(prev[cat] ?? []), ...incoming.map((f) => URL.createObjectURL(f))],
    }));
  };

  const onFolderUpload = (fileList: FileList | null) => {
    if (!fileList?.length) return;
    const result = groupFolderImages(fileList);
    for (const cat of CATEGORIES) {
      appendFilesToCategory(cat, result.grouped[cat]);
    }
    setPhotoUploadStatus(summarizeFolderUpload(result));
    if (folderInputRef.current) {
      folderInputRef.current.value = '';
    }
  };

  const onFilesChange = (cat: string, fileList: FileList | null) => {
    const nextFiles = Array.from(fileList ?? []);
    setFiles((prev) => ({ ...prev, [cat]: nextFiles }));
    setPreviews((prev) => {
      (prev[cat] ?? []).forEach((url) => URL.revokeObjectURL(url));
      return { ...prev, [cat]: nextFiles.map((f) => URL.createObjectURL(f)) };
    });
  };

  const removeFile = (cat: string, index: number) => {
    setFiles((prev) => {
      const next = [...(prev[cat] ?? [])];
      next.splice(index, 1);
      return { ...prev, [cat]: next };
    });
    setPreviews((prev) => {
      const urls = [...(prev[cat] ?? [])];
      URL.revokeObjectURL(urls[index]);
      urls.splice(index, 1);
      return { ...prev, [cat]: urls };
    });
  };

  useEffect(() => {
    void (async () => {
      try {
        setNaverSession(await fetchNaverSession());
      } catch {
        // ignore
      }
    })();
    return () => {
      if (naverPollRef.current != null) {
        window.clearInterval(naverPollRef.current);
      }
    };
  }, []);

  useEffect(() => {
    if (showNaverLogs) {
      naverLogEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [naverLogs, showNaverLogs]);

  const appendNaverLogs = (entries: NaverLogEntry[]) => {
    if (entries.length === 0) return;
    lastNaverLogIdRef.current = entries[entries.length - 1].id;
    setNaverLogs((prev) => [...prev, ...entries]);
  };

  const pollNaverJobStatus = async (jobId: string, onDone: () => void) => {
    const [job, logs] = await Promise.all([
      pollNaverJob(jobId),
      fetchNaverJobLogs(jobId, lastNaverLogIdRef.current),
    ]);
    appendNaverLogs(logs);
    setNaverStatus(job.message);
    if (job.status === 'RUNNING' || job.status === 'PENDING') {
      return;
    }
    onDone();
    if (job.status === 'COMPLETED') {
      setNaverSession(await fetchNaverSession());
      setStatus('원고 생성 및 네이버 임시저장 완료');
      setNaverStatus('네이버 임시저장 완료');
    }
    if (job.status === 'FAILED') {
      setError(job.message ?? '네이버 작업 실패');
      setStatus('원고 생성 완료 — 네이버 작성 실패');
    }
  };

  const startNaverJobPolling = (jobId: string, onDone: () => void) => {
    if (naverPollRef.current != null) window.clearInterval(naverPollRef.current);
    naverPollRef.current = window.setInterval(() => {
      void pollNaverJobStatus(jobId, onDone);
    }, 1500);
    void pollNaverJobStatus(jobId, onDone);
  };

  const onNaverBrowserLogin = async () => {
    setError('');
    setNaverBrowserBusy(true);
    setShowNaverLogs(true);
    setNaverLogs([]);
    lastNaverLogIdRef.current = 0;
    setNaverStatus('브라우저 창 준비 중…');
    try {
      const result = await startNaverBrowserLogin();
      if (!result.started || !result.jobId) {
        setError(result.message ?? '로그인 창 열기 실패');
        setNaverBrowserBusy(false);
        return;
      }
      startNaverJobPolling(result.jobId, () => {
        setNaverBrowserBusy(false);
        if (naverPollRef.current != null) {
          window.clearInterval(naverPollRef.current);
          naverPollRef.current = null;
        }
      });
    } catch (e) {
      setNaverBrowserBusy(false);
      setError(e instanceof Error ? e.message : '네이버 로그인 실패');
    }
  };

  const onNaverLogout = async () => {
    try {
      await logoutNaver();
      setNaverSession(await fetchNaverSession());
      setNaverStatus('로그아웃 완료');
    } catch (e) {
      setError(e instanceof Error ? e.message : '로그아웃 실패');
    }
  };

  const triggerNaverPublish = async (completedBlogJobId: string, options?: { auto?: boolean }) => {
    setError('');
    setNaverPublishing(true);
    setShowNaverLogs(true);
    if (options?.auto) {
      setNaverLogs([]);
      lastNaverLogIdRef.current = 0;
    }
    let session = naverSession;
    try {
      session = await fetchNaverSession();
      setNaverSession(session);
    } catch {
      // 세션 조회 실패 시 캐시된 상태 사용
    }
    const needsLogin = !session?.connected;
    setNaverStatus(
      needsLogin
        ? '브라우저 창에서 로그인하면 자동으로 임시저장합니다…'
        : '네이버 블로그 작성 중…',
    );
    if (options?.auto) {
      setStatus('원고 생성 완료 — 네이버 블로그 작성 중…');
    }
    try {
      const result = needsLogin
        ? await startNaverBrowserLogin(completedBlogJobId)
        : await startNaverPublish(completedBlogJobId);
      if (!result.started || !result.jobId) {
        setError(result.message ?? '네이버 작성 시작 실패');
        setNaverPublishing(false);
        return;
      }
      startNaverJobPolling(result.jobId, () => {
        setNaverPublishing(false);
        if (naverPollRef.current != null) {
          window.clearInterval(naverPollRef.current);
          naverPollRef.current = null;
        }
      });
    } catch (e) {
      setNaverPublishing(false);
      setError(e instanceof Error ? e.message : '네이버 작성 실패');
    }
  };

  const onNaverPublish = async () => {
    if (!blogJobId) {
      setError('먼저 원고 생성을 완료하세요.');
      return;
    }
    await triggerNaverPublish(blogJobId);
  };

  /** FormData 전송 후 Job 폴링 — 완료 시 downloadUrl 저장 */
  const onGenerate = async () => {
    if (!storeName.trim()) {
      setError('매장명을 입력하세요.');
      return;
    }
    if (!storeSearched && !infoText.trim()) {
      setError('원고 생성 전에 매장 정보를 조회해 주세요.');
      return;
    }
    setError('');
    setGenerating(true);
    setStatus('생성 중...');
    setBlogJobId('');
    setDownloadUrl('');
    const form = new FormData();
    form.append('storeName', storeName);
    form.append('region', region);
    form.append('postType', postType);
    form.append('bloggerName', bloggerName);
    form.append('rating', String(rating));
    form.append('infoText', infoText);
    form.append('link', link);
    form.append('campaignGuideline', campaignGuideline);
    for (const cat of CATEGORIES) {
      for (const file of files[cat] ?? []) {
        form.append(cat, file);
      }
    }
    try {
      const jobId = await startBlogJob(form);
      const interval = window.setInterval(async () => {
        const job = await pollBlogJob(jobId);
        if (job.status === 'COMPLETED') {
          window.clearInterval(interval);
          setBlogJobId(jobId);
          setDownloadUrl(job.downloadUrl ?? '');
          setGenerating(false);
          void triggerNaverPublish(jobId, { auto: true });
        } else if (job.status === 'FAILED') {
          window.clearInterval(interval);
          setError(job.message ?? '생성 실패');
          setStatus('');
          setGenerating(false);
        } else {
          setStatus(job.message ?? '처리 중...');
        }
      }, 2000);
    } catch (e) {
      setError(e instanceof Error ? e.message : '생성 실패');
      setStatus('');
      setGenerating(false);
    }
  };

  return (
    <section>
      <div className="page-hero">
        <div className="hero-eyebrow">AI Review</div>
        <h1>리뷰 블로그 작성</h1>
        <p className="hero-sub">사진과 매장 정보를 입력하면 Gemini가 리뷰 원고를 작성하고 Word 파일로 내보냅니다.</p>
      </div>

      {params.get('storeName') && (
        <span className="stat-pill accent">체험단 연동 · {params.get('storeName')}</span>
      )}

      <div className="panel naver-panel">
        <div className="panel-header-row">
          <h3>네이버 블로그</h3>
          {naverSession?.connected && (
            <span className="stat-pill accent">연결됨 · {naverSession.naverId}</span>
          )}
        </div>
        <p className="muted">
          원고 생성이 끝나면 <strong>자동으로</strong> 네이버 블로그 임시저장을 시도합니다.
          미연결 시 Chrome 창에서 직접 로그인하면 이어서 작성됩니다. (최대 5분 대기)
        </p>
        {!naverSession?.connected ? (
          <div className="field-row naver-login-row">
            <button type="button" onClick={() => void onNaverBrowserLogin()} disabled={naverBrowserBusy}>
              {naverBrowserBusy ? '로그인 대기 중…' : '네이버 로그인 창 열기'}
            </button>
          </div>
        ) : (
          <div className="field-row">
            <button type="button" className="secondary-btn" onClick={() => void onNaverLogout()}>
              연결 해제
            </button>
          </div>
        )}
        {(naverBrowserBusy || naverPublishing || showNaverLogs) && (
          <div className="fetch-log-panel naver-log-panel">
            <div className="fetch-log-header">
              <strong>네이버 작업 로그</strong>
              {(naverBrowserBusy || naverPublishing) && <span className="fetch-log-live">실시간</span>}
              <button type="button" className="fetch-log-close" onClick={() => setShowNaverLogs(false)} aria-label="로그 닫기">×</button>
            </div>
            <div className="fetch-log-body">
              {naverStatus && <p className="muted fetch-log-empty">{naverStatus}</p>}
              {naverLogs.map((entry) => (
                <div key={entry.id} className={`fetch-log-line level-${entry.level.toLowerCase()}`}>
                  <span className="fetch-log-time">{entry.time}</span>
                  <span className="fetch-log-msg">{entry.message}</span>
                </div>
              ))}
              <div ref={naverLogEndRef} />
            </div>
          </div>
        )}
      </div>

      <div className="panel">
        <h3>기본 정보</h3>
        <div className="field-row">
          <label className="field-inline">
            <span>유형</span>
            <select value={postType} onChange={(e) => setPostType(e.target.value)}>
              <option value="협찬">협찬</option>
              <option value="내돈내산">내돈내산</option>
            </select>
          </label>
          <label className="field-inline">
            <span>지역</span>
            <select value={region} onChange={(e) => setRegion(e.target.value)} disabled={regions.length === 0}>
              {(regions.length > 0 ? regions : [region]).map((v) => (
                <option key={v} value={v}>{v}</option>
              ))}
            </select>
          </label>
          <label className="field-inline field-grow">
            <span>매장명</span>
            <input value={storeName} onChange={(e) => setStoreName(e.target.value)} placeholder="매장명" />
          </label>
        </div>
        <div className="field-row" style={{ marginTop: '0.75rem' }}>
          <label className="field-inline field-grow">
            <span>블로그 명</span>
            <input value={bloggerName} onChange={(e) => setBloggerName(e.target.value)} placeholder="블로그 명 (기본값: 글로벌)" />
          </label>
        </div>
      </div>

      <div className="panel">
        <div className="panel-header-row">
          <h3>매장 정보</h3>
          <button type="button" onClick={() => void loadStoreInfo()} disabled={loadingStore}>
            {loadingStore ? '조회 중...' : '조회'}
          </button>
        </div>
        <p className="muted">매장명·지역 입력 후 조회 버튼을 눌러 공공 API + 네이버 정보를 가져옵니다.</p>

        {candidates.length > 1 && (
          <div className="field" style={{ marginTop: '0.75rem' }}>
            <label className="field-label">매장 선택</label>
            <select value={pickIndex} onChange={(e) => void onPickChange(Number(e.target.value))}>
              {candidates.map((item, idx) => (
                <option key={idx} value={idx}>{storeLabel(item)}</option>
              ))}
            </select>
          </div>
        )}

        <textarea
          value={infoText}
          onChange={(e) => setInfoText(e.target.value)}
          rows={8}
          className="full-width store-info-textarea"
          placeholder="조회 버튼을 누르면 매장 정보가 채워집니다."
        />
        <input
          value={link}
          onChange={(e) => setLink(e.target.value)}
          placeholder="네이버 지도 링크"
          className="full-width"
          style={{ marginTop: '0.5rem' }}
        />

        <div className="field" style={{ marginTop: '1rem' }}>
          <label className="field-label">평점</label>
          <p className="muted">선택한 별점에 맞게 원고 톤·총평·장단점 비율이 조절됩니다.</p>
          <div className="rating-box-grid" role="group" aria-label="평점 선택">
            {RATING_OPTIONS.map((value) => {
              const selected = rating === value;
              return (
                <label key={value} className={`rating-box${selected ? ' selected' : ''}`}>
                  <input
                    type="checkbox"
                    checked={selected}
                    onChange={() => setRating(value)}
                  />
                  <span className="rating-box-stars" aria-hidden="true">
                    {'★'.repeat(value)}{'☆'.repeat(5 - value)}
                  </span>
                  <span className="rating-box-label">{value}점</span>
                </label>
              );
            })}
          </div>
        </div>
      </div>

      <div className="panel">
        <h3>체험단 가이드라인</h3>
        <p className="muted">
          체험단 상세 페이지의 키워드·작성 요령을 붙여 넣으세요. 원고 생성 시에만 반영되며 저장되지 않습니다.
        </p>
        <textarea
          value={campaignGuideline}
          onChange={(e) => setCampaignGuideline(e.target.value)}
          rows={10}
          className="full-width guideline-input"
          placeholder="예)&#10;[키워드]&#10;대전 맛집, OO맛집&#10;&#10;[가이드라인]&#10;1. 사진 15장 이상&#10;2. 지도 삽입 필수&#10;3. 본문 1,000자 이상"
        />
      </div>

      <div className="panel">
        <div className="panel-header-row">
          <h3>사진 업로드</h3>
          <button
            type="button"
            className="secondary-btn"
            onClick={() => folderInputRef.current?.click()}
          >
            폴더 일괄 등록
          </button>
        </div>
        <input
          ref={(el) => {
            folderInputRef.current = el;
            if (el) {
              el.setAttribute('webkitdirectory', '');
              el.setAttribute('directory', '');
            }
          }}
          type="file"
          className="photo-upload-input"
          multiple
          onChange={(e) => onFolderUpload(e.target.files)}
        />
        <p className="muted">
          카테고리별로 사진을 올리거나, 상위 폴더를 선택하면{' '}
          <strong>external(외관)·interior(내부)·menu(메뉴)·product(음식)</strong> 하위 폴더명으로 자동 분류됩니다.
        </p>
        {photoUploadStatus && <p className="muted photo-upload-status">{photoUploadStatus}</p>}
        <div className="photo-upload-grid">
          {CATEGORIES.map((cat) => {
            const meta = PHOTO_META[cat];
            const count = files[cat]?.length ?? 0;
            return (
              <div className="photo-upload-card" key={cat}>
                <input
                  ref={(el) => { fileInputRefs.current[cat] = el; }}
                  type="file"
                  accept="image/*"
                  multiple
                  className="photo-upload-input"
                  onChange={(e) => onFilesChange(cat, e.target.files)}
                />
                <div
                  className="photo-upload-dropzone"
                  onClick={() => fileInputRefs.current[cat]?.click()}
                  onKeyDown={(e) => e.key === 'Enter' && fileInputRefs.current[cat]?.click()}
                  role="button"
                  tabIndex={0}
                >
                  <span className="photo-upload-icon">{meta.icon}</span>
                  <strong>{meta.label}</strong>
                  <span className="muted">{meta.hint}</span>
                  <span className="photo-upload-cta">{count > 0 ? `${count}장 선택됨 · 추가하기` : '클릭하여 사진 선택'}</span>
                </div>
                {(previews[cat]?.length ?? 0) > 0 && (
                  <div className="photo-preview-row">
                    {previews[cat].map((url, idx) => (
                      <div className="photo-preview-item" key={url}>
                        <img src={url} alt={`${meta.label} ${idx + 1}`} />
                        <button type="button" className="photo-remove-btn" onClick={() => removeFile(cat, idx)} aria-label="삭제">
                          ×
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>

      <button className="primary-btn" onClick={() => void onGenerate()} disabled={generating || naverPublishing}>
        {generating ? '원고 생성 중…' : naverPublishing ? '네이버 작성 중…' : '원고 생성'}
      </button>
      {downloadUrl && (
        <div className="field-row" style={{ marginTop: '0.75rem' }}>
          <a className="btn secondary" href={downloadUrl}>Word 다운로드</a>
          <button
            type="button"
            className="secondary-btn"
            onClick={() => void onNaverPublish()}
            disabled={naverPublishing || naverBrowserBusy}
          >
            {naverPublishing ? '네이버 작성 중…' : '네이버 다시 업로드'}
          </button>
        </div>
      )}
      {status && <p className="muted">{status}</p>}
      {error && <p className="error">{error}</p>}
    </section>
  );
}
