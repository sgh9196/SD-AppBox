/** React 앱 진입점 — BrowserRouter로 SPA 라우팅 */
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { RegionProvider } from './shared/RegionContext';
import './styles/index.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <RegionProvider>
        <App />
      </RegionProvider>
    </BrowserRouter>
  </StrictMode>,
);
