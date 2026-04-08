import { Redis } from 'ioredis';
import dotenv from 'dotenv';

dotenv.config();

const redisUrl = process.env.REDIS_URL || 'redis://localhost:6379';

export const redis = new Redis(redisUrl, {
  retryStrategy(times) {
    return Math.min(times * 50, 2000);
  },
});

export const CACHE_KEYS = {
  USER_STATUS: (userId: string) => `qunes:status:${userId}`,
  GHOST_MODE: (userId: string) => `qunes:ghost:${userId}`,
  SESSION: (token: string) => `qunes:session:${token}`,
};

/**
 * Updates user online status in Redis taking Ghost Mode into account
 */
export async function setUserPresence(userId: string, isOnline: boolean, suppressSync: boolean = false) {
  if (suppressSync) return;
  
  const statusKey = CACHE_KEYS.USER_STATUS(userId);
  if (isOnline) {
    await redis.set(statusKey, 'online', 'EX', 300); // 5 min expire if no heartbeat
  } else {
    await redis.set(statusKey, Date.now().toString());
  }
}

/**
 * Check if user is in Ghost Mode to bypass certain real-time status updates
 */
export async function isUserGhost(userId: string): Promise<boolean> {
  const ghost = await redis.get(CACHE_KEYS.GHOST_MODE(userId));
  return ghost === 'true';
}
