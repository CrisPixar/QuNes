import * as mediasoup from 'mediasoup';
import express from 'express';
import { createServer } from 'http';
import { Server } from 'socket.io';
import { config } from './config.js';
import dotenv from 'dotenv';

dotenv.config();

const app = express();
const httpServer = createServer(app);
const io = new Server(httpServer, { cors: { origin: '*' } });

let worker: mediasoup.types.Worker;
let router: mediasoup.types.Router;

async function runMediasoup() {
  worker = await mediasoup.createWorker(config.mediasoup.worker);
  router = await worker.createRouter(config.mediasoup.router);
  console.log('QuNes SFU Engine: Mediasoup Worker & Router active');
}

runMediasoup().catch(err => {
  console.error('SFU Core Boot Error:', err);
  process.exit(1);
});

const roomSessions: Map<string, Set<string>> = new Map();

io.on('connection', (socket) => {
  console.log('[Media-Signal] Node reached:', socket.id);

  socket.on('join-room', async ({ roomId }, callback) => {
    socket.join(roomId);
    if (!roomSessions.has(roomId)) roomSessions.set(roomId, new Set());
    roomSessions.get(roomId)?.add(socket.id);
    
    callback({ rtpCapabilities: router.rtpCapabilities });
  });

  socket.on('create-transport', async ({ roomId, direction }, callback) => {
    try {
      const transport = await router.createWebRtcTransport({
        listenIps: [{ ip: config.listenIp, announcedIp: config.announcedIp }],
        enableUdp: true,
        enableTcp: true,
        preferUdp: true,
        initialAvailableOutgoingBitrate: 1000000
      });

      transport.on('dtlsstatechange', (dtlsState) => {
        if (dtlsState === 'failed' || dtlsState === 'closed') transport.close();
      });

      callback({
        id: transport.id,
        iceParameters: transport.iceParameters,
        iceCandidates: transport.iceCandidates,
        dtlsParameters: transport.dtlsParameters
      });
    } catch (e) {
      console.error('Transport allocation fail:', e);
    }
  });

  // Step 11 Implementation: Quantum-secured signal negotiation
  socket.on('pqc-kem-init', ({ roomId, kyberPublicKey }) => {
     console.log(`[PQC-SFU] Client ${socket.id} broadcasting Kyber key to room ${roomId}`);
     socket.to(roomId).emit('pqc-peer-init', { senderId: socket.id, kyberPublicKey });
  });

  socket.on('pqc-kem-encaps', ({ roomId, recipientId, encryptedSecret }) => {
     console.log(`[PQC-SFU] Encapsulated secret delivery for call tunnel`);
     io.to(recipientId).emit('pqc-peer-encaps', { senderId: socket.id, encryptedSecret });
  });

  socket.on('ice-candidate', ({ roomId, candidate }) => {
    socket.to(roomId).emit('peer-ice-candidate', { senderId: socket.id, candidate });
  });


  // Step 12: Mediasoup SFU integration for post-quantum keys injection (SFrame signaling)
  socket.on('pqc-media-rotate', async ({ roomId, nextKeyId, encPublicData }) => {
    // Пересылаем данные о ротации всем участникам в сессии
    // Это позволяет клиентам обновить ключи в своих Insertable Streams на лету
    socket.to(roomId).emit('pqc-media-update', {
       keyId: nextKeyId,
       pqcWrap: encPublicData,
       originId: socket.id
    });
    console.log(`[PQC-SFU] Rotation triggered for session ${roomId}`);
  });
  socket.on('disconnect', () => {
    roomSessions.forEach((peers, roomId) => {
      if (peers.has(socket.id)) {
        peers.delete(socket.id);
        socket.to(roomId).emit('peer-left', { id: socket.id });
      }
    });
  });
});

const port = process.env.MEDIA_SERVER_PORT || 4000;
httpServer.listen(port, () => {
  console.log(`Media Layer 2 Active (QuNes Calling Node) | Port ${port}`);
});