import { PORT, ALLOWED_ORIGINS, JWT_SECRET_SEED } from "./constants.js";
import { getDB } from "./db/index.js";
import { checkHttpRateLimit, getClientIp } from "./middleware/rateLimit.js";
import { websocketHandler } from "./websocket/handler.js";
import {
  handleRegister,
  handleLogin,
  handleRefresh,
  handleLogout,
  handleGetSessions,
  handleRevokeSession,
  handleRevokeAllSessions,
} from "./routes/auth.js";
import { handleGetPrekeys, handleUploadPrekeys, handleGetKeyBundle } from "./routes/keys.js";
import { handleSearchUsers, handleGetUser } from "./routes/users.js";
import { handleGetChats, handleCreateChat, handleGetMessages } from "./routes/chats.js";
import {
  handleAdminStats,
  handleAdminGetUsers,
  handleAdminGetUser,
  handleAdminUpdateUser,
  handleAdminDeleteUser,
  handleAdminSetScam,
  handleAdminDeleteMessage,
  handleAdminDeleteAllMessages,
  handleAdminRevokeUserSessions,
} from "./routes/admin.js";

if (!/^[0-9a-f]{64}$/.test(JWT_SECRET_SEED)) {
  throw new Error("JWT_SECRET_SEED must contain exactly 64 hexadecimal characters");
}

getDB();

function corsHeaders(origin: string): Record<string, string> {
  const allowed = ALLOWED_ORIGINS.includes("*") || ALLOWED_ORIGINS.includes(origin);
  return {
    "Access-Control-Allow-Origin": allowed ? origin : "null",
    "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Authorization",
    "Access-Control-Max-Age": "86400",
    Vary: "Origin",
  };
}

Bun.serve({
  port: PORT,
  async fetch(req: Request, server: any) {
    const url = new URL(req.url);
    const origin = req.headers.get("origin") ?? "*";
    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders(origin) });
    if (url.pathname === "/ws") {
      if (server.upgrade(req, { data: {} })) return undefined;
      return new Response("WebSocket upgrade failed", { status: 400 });
    }
    if (!checkHttpRateLimit(getClientIp(req))) {
      return new Response(JSON.stringify({ error: "Too many requests" }), {
        status: 429,
        headers: { "Content-Type": "application/json; charset=utf-8", ...corsHeaders(origin) },
      });
    }

    let response: Response;
    try {
      response = await route(req, req.method, url.pathname);
    } catch (error) {
      console.error("[SERVER]", error);
      response = new Response(JSON.stringify({ error: "Internal server error" }), {
        status: 500,
        headers: { "Content-Type": "application/json; charset=utf-8" },
      });
    }
    for (const [key, value] of Object.entries(corsHeaders(origin))) response.headers.set(key, value);
    return response;
  },
  websocket: websocketHandler,
  error(error) {
    console.error("[FATAL]", error);
    return new Response("Server error", { status: 500 });
  },
});

async function route(req: Request, method: string, path: string): Promise<Response> {
  if (path === "/api/auth/register" && method === "POST") return handleRegister(req);
  if (path === "/api/auth/login" && method === "POST") return handleLogin(req);
  if (path === "/api/auth/refresh" && method === "POST") return handleRefresh(req);
  if (path === "/api/auth/logout" && method === "DELETE") return handleLogout(req);
  if (path === "/api/auth/sessions" && method === "GET") return handleGetSessions(req);
  if (path === "/api/auth/sessions" && method === "DELETE") return handleRevokeAllSessions(req);

  const ownSession = path.match(/^\/api\/auth\/sessions\/([^/]+)$/);
  if (ownSession && method === "DELETE") return handleRevokeSession(req, ownSession[1]);

  if (path === "/api/keys/prekeys" && method === "POST") return handleUploadPrekeys(req);
  const prekeys = path.match(/^\/api\/keys\/prekeys\/([^/]+)$/);
  if (prekeys && method === "GET") return handleGetPrekeys(req, prekeys[1]);
  const bundle = path.match(/^\/api\/keys\/bundle\/([^/]+)$/);
  if (bundle && method === "GET") return handleGetKeyBundle(req, bundle[1]);

  if (path === "/api/users/search" && method === "GET") return handleSearchUsers(req);
  const user = path.match(/^\/api\/users\/([^/]+)$/);
  if (user && method === "GET") return handleGetUser(req, user[1]);

  if (path === "/api/chats" && method === "GET") return handleGetChats(req);
  if (path === "/api/chats" && method === "POST") return handleCreateChat(req);
  const messages = path.match(/^\/api\/chats\/([^/]+)\/messages$/);
  if (messages && method === "GET") return handleGetMessages(req, messages[1]);

  if (path === "/api/admin/stats" && method === "GET") return handleAdminStats(req);
  if (path === "/api/admin/users" && method === "GET") return handleAdminGetUsers(req);
  const adminUser = path.match(/^\/api\/admin\/users\/([^/]+)$/);
  if (adminUser && method === "GET") return handleAdminGetUser(req, adminUser[1]);
  if (adminUser && method === "PUT") return handleAdminUpdateUser(req, adminUser[1]);
  if (adminUser && method === "DELETE") return handleAdminDeleteUser(req, adminUser[1]);
  const scam = path.match(/^\/api\/admin\/users\/([^/]+)\/scam$/);
  if (scam && method === "POST") return handleAdminSetScam(req, scam[1]);
  const sessions = path.match(/^\/api\/admin\/users\/([^/]+)\/sessions$/);
  if (sessions && method === "DELETE") return handleAdminRevokeUserSessions(req, sessions[1]);
  const message = path.match(/^\/api\/admin\/messages\/([^/]+)$/);
  if (message && method === "DELETE") return handleAdminDeleteMessage(req, message[1]);
  const chatMessages = path.match(/^\/api\/admin\/chats\/([^/]+)\/messages$/);
  if (chatMessages && method === "DELETE") return handleAdminDeleteAllMessages(req, chatMessages[1]);

  if (path === "/health") {
    return new Response(JSON.stringify({ status: "ok", timestamp: Date.now() }), {
      headers: { "Content-Type": "application/json; charset=utf-8" },
    });
  }
  return new Response(JSON.stringify({ error: "Not found" }), {
    status: 404,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}

console.log(`QNS server listening on port ${PORT}`);
