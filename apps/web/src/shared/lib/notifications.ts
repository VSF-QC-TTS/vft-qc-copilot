import { toast } from 'sonner';
import type { ExternalToast } from 'sonner';

export interface NotificationOptions {
  id?: string | number;
  duration?: number;
  dedupeKey?: string;
  dedupeWindowMs?: number;
}

type NotificationKind = 'error' | 'info' | 'success' | 'warning';

const DEFAULT_DEDUPE_WINDOW_MS = 4000;
const recentNotifications = new Map<string, number>();

function notify(
  kind: NotificationKind,
  message: string,
  defaultDuration: number,
  options?: NotificationOptions,
) {
  const now = Date.now();
  const dedupeWindowMs = options?.dedupeWindowMs ?? DEFAULT_DEDUPE_WINDOW_MS;
  const dedupeKey = `${kind}:${options?.dedupeKey ?? message}`;

  for (const [key, expiresAt] of recentNotifications) {
    if (expiresAt <= now) {
      recentNotifications.delete(key);
    }
  }

  if ((recentNotifications.get(dedupeKey) ?? 0) > now) {
    return undefined;
  }

  recentNotifications.set(dedupeKey, now + dedupeWindowMs);

  const toastOptions: ExternalToast = { duration: options?.duration ?? defaultDuration };
  if (options?.id !== undefined) {
    toastOptions.id = options.id;
  }

  return toast[kind](message, toastOptions);
}

export function notifySuccess(message: string, options?: NotificationOptions) {
  return notify('success', message, 4000, options);
}

export function notifyInfo(message: string, options?: NotificationOptions) {
  return notify('info', message, 5000, options);
}

export function notifyWarning(message: string, options?: NotificationOptions) {
  return notify('warning', message, 7000, options);
}

export function notifyError(message: string, options?: NotificationOptions) {
  return notify('error', message, 8000, options);
}
