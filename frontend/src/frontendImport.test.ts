import { describe, it, expect } from 'vitest';

import './App';
import './blog/api/client';
import './blog/pages/BlogPage';
import './blog/pages/CampaignPage';
import './app-box/pages/AppBoxPage';

describe('frontend import smoke', () => {
  it('loads core modules', () => {
    expect(true).toBe(true);
  });
});
