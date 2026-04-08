import Fastify from 'fastify';
import fastifyStatic from '@fastify/static';
import { Server } from 'socket.io';
import * as path from 'path';
import { fileURLToPath } from 'url';
import { adminRoutes } from './routes/admin.js';
import { setupSocket } from './services/socket.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const server = Fastify({ logger: true });

server.register(fastifyStatic, {
  root: path.join(__dirname, '../public'),
  prefix: '/public/',
});

server.register(adminRoutes, { prefix: '/white-admin' });

const start = async () => {
  try {
    await server.listen({ port: 3000, host: '0.0.0.0' });
  } catch (err) {
    server.log.error(err);
    process.exit(1);
  }
};

start();
