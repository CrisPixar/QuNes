import { RATE_LIMIT_HTTP_RPS, RATE_LIMIT_WS_PER_MIN } from "../constants.js";

interface Bucket { count: number; resetAt: number; }
const httpBuckets = new Map<string, Bucket>();
const wsBuckets   = new Map<string, Bucket>();

// Очищаем просроченные записи каждые 5 минут
setInterval(() => {
  const now = Date.now();
  for (const [k, v] of httpBuckets) if (v.resetAt < now) httpBuckets.delete(k);
  for (const [k, v] of wsBuckets)   if (v.resetAt < now) wsBuckets.delete(k);
}, 300_000);

export function checkHttpRateLimit(ip: string): boolean {
  const now = Date.now();
  let b = httpBuckets.get(ip);
  if (!b || b.resetAt < now) { httpBuckets.set(ip, { count: 1, resetAt: now + 1000 }); return true; }
  if (b.count >= RATE_LIMIT_HTTP_RPS) return false;
  b.count++;
  return true;
}

export function checkWsRateLimit(userId: string): boolean {
  const now = Date.now();
  let b = wsBuckets.get(userId);
  if (!b || b.resetAt < now) { wsBuckets.set(userId, { count: 1, resetAt: now + 60_000 }); return true; }
  if (b.count >= RATE_LIMIT_WS_PER_MIN) return false;
  b.count++;
  return true;
}

export function getClientIp(req: Request): string {
  return (
    req.headers.get("x-forwarded-for")?.split(",")[0].trim() ??
    req.headers.get("x-real-ip") ??
    "unknown"
  );
}
