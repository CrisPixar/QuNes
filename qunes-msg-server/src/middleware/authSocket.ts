import { Socket } from 'socket.io';
import prisma from '../services/prisma.js';
import { redis, CACHE_KEYS } from '../services/redis.js';

export const authMiddleware = async (socket: Socket, next: (err?: Error) => void) => {
  const token = socket.handshake.auth.token || socket.handshake.query.token;
  const userId = socket.handshake.query.userId as string;

  if (!token || !userId) {
    return next(new Error('Authentication failed: Missing credentials'));
  }

  try {
    // Check Redis for active session
    const sessionData = await redis.get(CACHE_KEYS.SESSION(token));
    if (!sessionData && process.env.NODE_ENV === 'production') {
      // verify via DB if cache missed
      const session = await prisma.session.findUnique({
        where: { token, userId }
      });
      if (!session || session.expiresAt < new Date()) {
        return next(new Error('Session expired or invalid'));
      }
    }

    // Check user keys presence
    const user = await prisma.user.findUnique({ where: { id: userId } });
    if (!user) return next(new Error('User not found'));

    socket.data.user = user;
    next();
  } catch (error) {
    next(new Error('Internal auth error'));
  }
};