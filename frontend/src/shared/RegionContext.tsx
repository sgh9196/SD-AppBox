import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useSearchParams } from 'react-router-dom';
import { fetchCampaignRegions, type CampaignCard } from '../blog/api/client';
import {
  loadStoredRegion,
  pickValidRegion,
  regionFromSearchParam,
  saveRegion,
  uniqueRegions,
} from './region';

type RegionContextValue = {
  region: string;
  regions: string[];
  setRegion: (region: string) => void;
  syncRegionsFromCards: (cards: CampaignCard[]) => void;
  loadRegions: (refresh?: boolean) => Promise<void>;
};

const RegionContext = createContext<RegionContextValue | null>(null);

export function RegionProvider({ children }: { children: ReactNode }) {
  const [searchParams] = useSearchParams();
  const [regions, setRegions] = useState<string[]>([]);
  const [region, setRegionState] = useState<string>(() => {
    return regionFromSearchParam(searchParams.get('region')) ?? loadStoredRegion();
  });

  const applyRegions = useCallback((nextRegions: string[], preferred?: string) => {
    if (nextRegions.length === 0) {
      return;
    }
    setRegions(nextRegions);
    setRegionState((current) => {
      const valid = pickValidRegion(preferred ?? current, nextRegions);
      saveRegion(valid);
      return valid;
    });
  }, []);

  const syncRegionsFromCards = useCallback((cards: CampaignCard[]) => {
    applyRegions(uniqueRegions(cards));
  }, [applyRegions]);

  const loadRegions = useCallback(async (refresh = false) => {
    const nextRegions = await fetchCampaignRegions(refresh);
    applyRegions(nextRegions);
  }, [applyRegions]);

  useEffect(() => {
    void loadRegions();
  }, [loadRegions]);

  useEffect(() => {
    const fromUrl = regionFromSearchParam(searchParams.get('region'));
    if (!fromUrl) {
      return;
    }
    setRegionState(() => {
      const valid = regions.length > 0 ? pickValidRegion(fromUrl, regions) : fromUrl;
      saveRegion(valid);
      return valid;
    });
  }, [searchParams, regions]);

  const setRegion = useCallback((next: string) => {
    setRegionState(next);
    saveRegion(next);
  }, []);

  const value = useMemo(
    () => ({ region, regions, setRegion, syncRegionsFromCards, loadRegions }),
    [region, regions, setRegion, syncRegionsFromCards, loadRegions],
  );

  return <RegionContext.Provider value={value}>{children}</RegionContext.Provider>;
}

export function useRegion(): RegionContextValue {
  const ctx = useContext(RegionContext);
  if (!ctx) {
    throw new Error('useRegion must be used within RegionProvider');
  }
  return ctx;
}
