import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  verifyAuthCode,
  issueAuthCode,
  fetchAuthCodes,
  deleteAuthCode
} from '../../blog/api/client';

export default function AppBoxPage() {
  const navigate = useNavigate();
  const [showModal, setShowModal] = useState(false);
  const [inputCode, setInputCode] = useState('');
  const [errorMsg, setErrorMsg] = useState('');


  // 관리자 관련 상태
  const [showAdminModal, setShowAdminModal] = useState(false);
  const [adminCodeInput, setAdminCodeInput] = useState('');
  const [adminStep, setAdminStep] = useState<'login' | 'panel'>('login');
  const [adminError, setAdminError] = useState('');
  const [adminCode, setAdminCode] = useState('');
  const [newCodeInput, setNewCodeInput] = useState('');
  const [allowGeulobel, setAllowGeulobel] = useState(false);
  const [allowMarketing, setAllowMarketing] = useState(false);
  const [allowInfluencer, setAllowInfluencer] = useState(false);
  const [issuedCodes, setIssuedCodes] = useState<Record<string, string[]>>({});

  const handleCardClick = () => {
    setShowModal(true);
    setInputCode('');
    setErrorMsg('');
  };

  const handleCloseModal = () => {
    setShowModal(false);
    setInputCode('');
    setErrorMsg('');
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!inputCode.trim()) {
      setErrorMsg('코드를 입력하세요.');
      return;
    }
    setErrorMsg('');
    try {
      const res = await verifyAuthCode(inputCode.trim());
      if (res.valid) {
        sessionStorage.setItem('auth_code', inputCode.trim());
        sessionStorage.setItem('allowed_apps', JSON.stringify(res.allowedApps));
        setShowModal(false);
        if (res.allowedApps.includes('geulobel')) {
          navigate('/campaign');
        } else {
          setErrorMsg('글로벌 서비스 접근 권한이 없는 코드입니다.');
        }
      } else {
        setErrorMsg('코드가 일치하지 않거나 유효하지 않습니다.');
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '인증 과정 중 에러가 발생했습니다.';
      setErrorMsg(msg);
    }
  };

  // 관리자 모달 제어
  const handleOpenAdminModal = () => {
    setShowAdminModal(true);
    setAdminCodeInput('');
    setAdminStep('login');
    setAdminError('');
    setAdminCode('');
    setNewCodeInput('');
    setAllowGeulobel(false);
    setAllowMarketing(false);
    setAllowInfluencer(false);
  };

  const handleCloseAdminModal = () => {
    setShowAdminModal(false);
  };

  const handleAdminLoginSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!adminCodeInput.trim()) {
      setAdminError('관리자 코드를 입력하세요.');
      return;
    }
    setAdminError('');
    try {
      const res = await verifyAuthCode(adminCodeInput.trim());
      if (res.valid && res.isAdmin) {
        setAdminCode(adminCodeInput.trim());
        setAdminStep('panel');
        await loadCodes(adminCodeInput.trim());
      } else {
        setAdminError('올바른 관리자 코드가 아닙니다.');
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '인증에 실패했습니다.';
      setAdminError(msg);
    }
  };

  const loadCodes = async (admCode: string) => {
    try {
      const list = await fetchAuthCodes(admCode);
      setIssuedCodes(list);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '코드 리스트 조회 실패';
      setAdminError(msg);
    }
  };

  const handleIssueCodeSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newCodeInput.trim()) {
      alert('발급할 코드를 입력하세요.');
      return;
    }
    const apps: string[] = [];
    if (allowGeulobel) apps.push('geulobel');
    if (allowMarketing) apps.push('marketing');
    if (allowInfluencer) apps.push('influencer');

    if (apps.length === 0) {
      alert('최소 하나 이상의 권한을 선택해야 합니다.');
      return;
    }

    try {
      const res = await issueAuthCode(adminCode, newCodeInput.trim(), apps);
      if (res.success) {
        setNewCodeInput('');
        setAllowGeulobel(false);
        setAllowMarketing(false);
        setAllowInfluencer(false);
        await loadCodes(adminCode);
      } else {
        alert(res.message || '코드 발급 실패');
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '코드 발급 중 에러가 발생했습니다.';
      alert(msg);
    }
  };

  const handleDeleteCode = async (codeToDelete: string) => {
    if (!confirm(`'${codeToDelete}' 코드를 파기하시겠습니까?`)) {
      return;
    }
    try {
      const res = await deleteAuthCode(adminCode, codeToDelete);
      if (res.success) {
        await loadCodes(adminCode);
      } else {
        alert('코드 삭제 실패');
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '코드 삭제 중 에러가 발생했습니다.';
      alert(msg);
    }
  };

  return (
    <div className="appbox-container">
      {/* 관리자 진입 트리거 */}
      <button className="appbox-admin-trigger" onClick={handleOpenAdminModal}>
        ⚙️ 관리자 설정
      </button>

      <div className="appbox-header">
        <div className="appbox-brand">
          <strong>SD-App</strong>
          <span>Box</span>
        </div>
        <h1>원하는 서비스 영역을 선택하세요</h1>
        <p className="appbox-subtitle">
          SD-App Box의 다양한 작업 공간과 도구에 즉시 진입할 수 있습니다.
        </p>
      </div>

      <div className="appbox-grid">
        {/* 활성화 카드: 글로벌 (Geul-o-bel) */}
        <div
          className="appbox-card active-card"
          onClick={handleCardClick}
        >
          <div className="appbox-card-thumb-wrap">
            <img
              src="/studio_portal_bg.png"
              alt="글로벌 (Geul-o-bel)"
              className="appbox-card-thumb"
            />
            <span className="appbox-status-badge online">ONLINE</span>
          </div>
          <div className="appbox-card-body">
            <h2>글로벌 (Geul-o-bel)</h2>
            <p>체험단 정보 수집 및 Gemini 기반 리뷰 블로그 원고 자동 완성 워크스페이스</p>
            <div className="appbox-card-action">
              <span>입장하기</span>
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2.5"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <path d="M5 12h14M12 5l7 7-7 7" />
              </svg>
            </div>
          </div>
        </div>

        {/* 비활성화 카드 1: 마케팅 성과 대시보드 */}
        <div className="appbox-card disabled-card">
          <div className="appbox-card-thumb-wrap">
            <div className="appbox-card-thumb-placeholder stats-bg">
              <span>Coming Soon</span>
            </div>
            <span className="appbox-status-badge pending">PREPARING</span>
          </div>
          <div className="appbox-card-body">
            <h2>마케팅 성과 분석</h2>
            <p>네이버 노출 데이터 및 마케팅 성과를 시각적으로 추적하는 스마트 대시보드</p>
            <div className="appbox-card-action">
              <span>준비 중</span>
            </div>
          </div>
        </div>

        {/* 비활성화 카드 2: 인플루언서 랭킹 보드 */}
        <div className="appbox-card disabled-card">
          <div className="appbox-card-thumb-wrap">
            <div className="appbox-card-thumb-placeholder rank-bg">
              <span>Coming Soon</span>
            </div>
            <span className="appbox-status-badge pending">PREPARING</span>
          </div>
          <div className="appbox-card-body">
            <h2>인플루언서 랭킹</h2>
            <p>지역별, 키워드별 인플루언서 지수 및 블로그 파워 분석 테이블</p>
            <div className="appbox-card-action">
              <span>준비 중</span>
            </div>
          </div>
        </div>
      </div>

      {/* 글래스모피즘 비밀 코드 입력 모달 */}
      {showModal && (
        <>
          <div className="appbox-modal-overlay" onClick={handleCloseModal} />
          <div className="appbox-modal">
            <div className="appbox-modal-header">
              <h2>🔒 보안 코드 확인</h2>
              <p>이 서비스 영역에 진입하려면 접근 코드가 필요합니다.</p>
            </div>
            <form onSubmit={handleSubmit} className="appbox-modal-form">
              <div className="field">
                <input
                  type="password"
                  placeholder="보안 코드를 입력하세요"
                  value={inputCode}
                  onChange={(e) => setInputCode(e.target.value)}
                  className={`full-width ${errorMsg ? 'input-error' : ''}`}
                  autoFocus
                />
                {errorMsg && <div className="error">{errorMsg}</div>}
              </div>
              <div className="appbox-modal-actions">
                <button
                  type="button"
                  className="secondary-btn"
                  onClick={handleCloseModal}
                >
                  취소
                </button>
                <button type="submit" className="primary-btn">
                  확인
                </button>
              </div>
            </form>
          </div>
        </>
      )}

      {/* 관리자용 설정 패널 모달 */}
      {showAdminModal && (
        <>
          <div className="appbox-modal-overlay" onClick={handleCloseAdminModal} />
          <div className={`appbox-modal ${adminStep === 'panel' ? 'admin-modal' : ''}`}>
            <div className="appbox-modal-header">
              <h2>⚙️ 관리자 설정 패널</h2>
              <p>
                {adminStep === 'login'
                  ? '관리자 코드를 입력하여 패널에 접근하십시오.'
                  : '접근용 사용자 보안 코드를 발급하거나 파기할 수 있습니다.'
                }
              </p>
            </div>

            {adminStep === 'login' ? (
              <form onSubmit={handleAdminLoginSubmit} className="appbox-modal-form">
                <div className="field">
                  <input
                    type="password"
                    placeholder="관리자 코드를 입력하세요"
                    value={adminCodeInput}
                    onChange={(e) => setAdminCodeInput(e.target.value)}
                    className={`full-width ${adminError ? 'input-error' : ''}`}
                    autoFocus
                  />
                  {adminError && <div className="error">{adminError}</div>}
                </div>
                <div className="appbox-modal-actions">
                  <button
                    type="button"
                    className="secondary-btn"
                    onClick={handleCloseAdminModal}
                  >
                    닫기
                  </button>
                  <button type="submit" className="primary-btn">
                    인증
                  </button>
                </div>
              </form>
            ) : (
              <div style={{ marginTop: '1.5rem' }}>
                {/* 코드 발급 양식 */}
                <form onSubmit={handleIssueCodeSubmit} className="admin-section">
                  <h3>🔑 신규 보안 코드 발급</h3>
                  <div className="admin-form-row">
                    <input
                      type="text"
                      placeholder="발급할 보안 코드를 입력하세요"
                      value={newCodeInput}
                      onChange={(e) => setNewCodeInput(e.target.value)}
                      style={{ flex: 1 }}
                    />
                    <button type="submit" className="primary-btn" style={{ flex: 'initial', padding: '0.65rem 1.25rem' }}>
                      발급하기
                    </button>
                  </div>
                  <div className="admin-checkboxes">
                    <label>
                      <input
                        type="checkbox"
                        checked={allowGeulobel}
                        onChange={(e) => setAllowGeulobel(e.target.checked)}
                      />
                      글로벌 (Geul-o-bel)
                    </label>
                    <label style={{ opacity: 0.5, cursor: 'not-allowed' }} title="현재 비활성화된 앱입니다.">
                      <input
                        type="checkbox"
                        checked={false}
                        disabled={true}
                      />
                      마케팅 성과 분석 (준비 중)
                    </label>
                    <label style={{ opacity: 0.5, cursor: 'not-allowed' }} title="현재 비활성화된 앱입니다.">
                      <input
                        type="checkbox"
                        checked={false}
                        disabled={true}
                      />
                      인플루언서 랭킹 (준비 중)
                    </label>
                  </div>
                </form>

                {/* 발급된 코드 리스트 */}
                <div className="admin-section">
                  <h3>📋 현재 발급된 활성 코드 목록</h3>
                  {Object.keys(issuedCodes).length === 0 ? (
                    <p style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)', margin: '1rem 0 0' }}>
                      발급된 사용자 코드가 없습니다.
                    </p>
                  ) : (
                    <div className="admin-code-list">
                      <table className="admin-table">
                        <thead>
                          <tr>
                            <th>보안 코드</th>
                            <th>허용된 권한</th>
                            <th style={{ width: '60px', textAlign: 'center' }}>관리</th>
                          </tr>
                        </thead>
                        <tbody>
                          {Object.entries(issuedCodes).map(([code, apps]) => (
                            <tr key={code}>
                              <td style={{ fontWeight: 700 }}>{code}</td>
                              <td>
                                {apps.includes('geulobel') && <span className="admin-badge campaign">글로벌</span>}
                                {apps.includes('marketing') && <span className="admin-badge blog">성과 분석</span>}
                                {apps.includes('influencer') && <span className="admin-badge blog" style={{ background: 'rgba(59, 130, 246, 0.15)', color: '#3B82F6', borderColor: 'rgba(59, 130, 246, 0.25)' }}>인플루언서</span>}
                                {apps.length === 0 && <span style={{ color: 'var(--color-text-muted)' }}>권한 없음</span>}
                              </td>
                              <td style={{ textAlign: 'center' }}>
                                <button
                                  className="admin-delete-btn"
                                  onClick={() => handleDeleteCode(code)}
                                  title="권한 파기"
                                >
                                  파기
                                </button>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </div>

                <div className="appbox-modal-actions">
                  <button
                    type="button"
                    className="secondary-btn"
                    onClick={handleCloseAdminModal}
                  >
                    패널 닫기
                  </button>
                </div>
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}
