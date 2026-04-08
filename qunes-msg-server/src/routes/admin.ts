import { FastifyInstance } from 'fastify';
import prisma from '../services/prisma.js';
import { redis } from '../services/redis.js';

export default async function adminRoutes(fastify: FastifyInstance) {
  fastify.post('/login', async (request, reply) => {
    const { username, password } = request.body as any;
    if (username === process.env.ADMIN_USERNAME && password === process.env.ADMIN_PASSWORD) {
      return { success: true, 
      token: 'secure_auth_pqc_session_v1', 
      role: 'SuperAdmin',
      user: 'Whitekiller' };
    }
    return reply.status(401).send({ error: 'Unauthorized' });
  });

  fastify.get('/stats', async (request, reply) => {
    // Basic system metrics for dashboard
    const userCount = await prisma.user.count();
    const messageCount = await prisma.message.count();
    const onlineUsers = (await redis.keys('qunes:status:*')).length;

    return {
      users: userCount,
      messages: messageCount,
      online: onlineUsers,
      cpu: Math.random() * 100, // Simulation of load
      memory: process.memoryUsage().heapUsed / 1024 / 1024
    };
  });

  fastify.get('/users', async () => {
    return prisma.user.findMany({
      select: {
        id: true,
        username: true,
        ghostMode: true,
        createdAt: true
      }
    });
  });
}