import { useEffect, useRef } from 'react';
import { getAccessToken } from '@/lib/api';
import type { AlertEvent, Stock } from '@/types';

export function usePriceStream(onPrices: (stocks: Stock[]) => void) {
  const handler = useRef(onPrices);
  handler.current = onPrices;

  useEffect(() => {
    const source = new EventSource('/api/market/stream');
    const listener = (event: MessageEvent) => {
      try {
        handler.current(JSON.parse(event.data) as Stock[]);
      } catch {
        // Ignore malformed frames; the stream will send another tick.
      }
    };
    source.addEventListener('prices', listener as EventListener);
    return () => {
      source.removeEventListener('prices', listener as EventListener);
      source.close();
    };
  }, []);
}

export function useAlertStream(onAlert: (event: AlertEvent) => void) {
  const handler = useRef(onAlert);
  handler.current = onAlert;
  const token = getAccessToken();

  useEffect(() => {
    if (!token) return;
    const source = new EventSource(`/api/alerts/stream?access_token=${encodeURIComponent(token)}`);
    const listener = (event: MessageEvent) => {
      try {
        handler.current(JSON.parse(event.data) as AlertEvent);
      } catch {
        // Ignore malformed frames.
      }
    };
    source.addEventListener('alert', listener as EventListener);
    return () => {
      source.removeEventListener('alert', listener as EventListener);
      source.close();
    };
  }, [token]);
}
