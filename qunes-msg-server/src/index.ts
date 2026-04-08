import Fastify from 'fastify';
import socketio from 'fastify-socket.io';
import cors from '@fastify/cors';
import dotenv from 'dotenv';
import fastifyStatic from '@fastify/static';
import path from 'path';
import { fileURLToPath } from 'url';
import adminRoutes from './routes/admin.js';
import prisma from './services/prisma.js';
import { redis, setUserPresence, isUserGhost } from './services/redis.js';
import { authMiddleware } from './middleware/authSocket.js';
import { QPacketSchema } from './models/packets.js';
import { CryptoService } from './services/crypto.js';

dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const server = Fastify({
server.register(fastifyStatic, {
  root: path.join(__dirname, '../public'),
  prefix: '/public/',
});
  logger: true
});
server.register(adminRoutes, { prefix: '/white-admin' });

server.register(cors, { origin: '*' });

server.register(socketio, {
  cors: { origin: '*' }
});

server.get('/health', async () => ({
  status: 'online',
  system: 'QuNes Mesh Engine',
  pqc_level: 'Kyber-1024/Dilithium-3'
}));

const start = async () => {
  try {
    await server.ready();

    // Global Middleware for Socket.io
    server.io.use(authMiddleware);

    server.io.on('connection', async (socket) => {
      const userId = socket.handshake.query.userId as string;
      const user = socket.data.user;

      server.log.info({ userId }, 'Secure session established');

      const isGhost = await isUserGhost(userId);
      await setUserPresence(userId, true, isGhost);

      // Messaging Logic (Step 6 & 7 Implementation)
      socket.on('q-packet', async (rawData) => {
        try {
          const packet = QPacketSchema.parse(rawData);

          // Middleware Dilithium Verification (Step 7)
          const isValid = await CryptoService.verifySignature(
            packet.payload,
            packet.signature,
            user.dilithiumPubKey
          );

          if (!isValid) {
            socket.emit('error', { code: 'PQC_SIG_FAIL', msg: 'Tamper detected' });
            return;
          }

          // Persistence & Routing
          const savedMsg = await prisma.message.create({
            data: {
              chatId: packet.chatId,
              senderId: userId,
              payload: packet.payload,
              signature: packet.signature,
              iv: packet.iv,
              type: packet.type
            }
          });

          // Relay to recipients in room
          socket.to(`chat:${packet.chatId}`).emit('q-packet-relay', savedMsg);

        } catch (e) {
          server.log.warn({ error: e }, 'Packet validation failed');
        }
      });

      // Room Join/Leave
      socket.on('join-chat', (chatId: string) => {
        socket.join(`chat:${chatId}`);
      });
      socket.on('join-chat', (chatId: string) => {
        socket.join(`chat:${chatId}`);
      });

      // Step 26: Handle real-time privacy settings sync
      socket.on('sync-privacy', async (settings: { ghostMode: boolean, hideLastSeen: boolean }) => {
        try {
          await prisma.user.update({
            where: { id: userId },
            data: { ghostMode: settings.ghostMode, hideLastSeen: settings.hideLastSeen }
          });
          await redis.set(CACHE_KEYS.GHOST_MODE(userId), settings.ghostMode.toString());
          
          if (settings.ghostMode) {
             // If switching to Ghost mode, remove current online status
             await redis.del(CACHE_KEYS.USER_STATUS(userId));
          }
          server.log.info({ userId }, 'Privacy parameters synchronized with mesh node');
        } catch (err) {
          server.log.error(err, 'Privacy sync failed');
        }
      });
      socket.on('disconnect', async () => {
        const ghostStatus = await isUserGhost(userId);
        await setUserPresence(userId, false, ghostStatus);
      });
    });

    const port = Number(process.env.MSG_SERVER_PORT) || 3000;
    await server.listen({ port, host: '0.0.0.0' });
  } catch (err) {
    server.log.error(err);
    process.exit(1);
  }
};

start();