/**
 * 앱 레이아웃 — 좌측 사이드바 + 메인 콘텐츠.
 * /campaign(기본), /blog, /public 라우트.
 */
import { type ReactNode } from 'react';
import { NavLink, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import BlogPage from './blog/pages/BlogPage';
import CampaignPage from './blog/pages/CampaignPage';
import AppBoxPage from './app-box/pages/AppBoxPage';
import { useRegion } from './shared/RegionContext';

function NavItem({ to, children }: { to: string; children: ReactNode }) {
  const { region } = useRegion();
  return (
    <NavLink to={`${to}?region=${encodeURIComponent(region)}`}>
      {children}
    </NavLink>
  );
}

export default function App() {
  const location = useLocation();
  const navigate = useNavigate();
  const isPortal = location.pathname === '/';

  const allowedAppsRaw = sessionStorage.getItem('allowed_apps');
  const allowedApps: string[] = allowedAppsRaw ? JSON.parse(allowedAppsRaw) : [];

  // 사이드바의 글로벌 로고 클릭 시 세션을 지우고 포털로 돌아갌 함
  const handlePortalNav = () => {
    sessionStorage.removeItem('auth_code');
    sessionStorage.removeItem('allowed_apps');
    navigate('/');
  };

  if (isPortal) {
    return (
      <Routes>
        <Route path="/" element={<AppBoxPage />} />
      </Routes>
    );
  }

  // Router guard
  const isAuthorized = allowedApps.includes('geulobel');

  if (!isAuthorized) {
    return <Navigate to="/" replace />;
  }

  return (
    <div className="app">
      <aside className="sidebar">
        <div className="brand">
          <button
            onClick={handlePortalNav}
            style={{ background: 'none', border: 'none', cursor: 'pointer', textDecoration: 'none', padding: 0 }}
          >
            <strong>글로벌</strong>
            <span>(Geul-o-bel)</span>
          </button>
        </div>
        <nav>
          <NavItem to="/campaign">체험단</NavItem>
          <NavItem to="/blog">리뷰 블로그</NavItem>
        </nav>
      </aside>
      <main className="content">
        <Routes>
          <Route path="/campaign" element={<CampaignPage />} />
          <Route path="/blog" element={<BlogPage />} />
        </Routes>
      </main>
    </div>
  );
}
