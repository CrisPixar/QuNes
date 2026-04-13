import { PORT, ALLOWED_ORIGINS }             from "./constants.js";
import { getDB }                             from "./db/index.js";
import { checkHttpRateLimit, getClientIp }   from "./middleware/rateLimit.js";
import { websocketHandler }                  from "./websocket/handler.js";
import { handleRegister, handleLogin, handleRefresh, handleLogout } from "./routes/auth.js";
import { handleGetPrekeys, handleUploadPrekeys, handleGetKeyBundle } from "./routes/keys.js";
import { handleSearchUsers, handleGetUser }  from "./routes/users.js";
import { handleGetChats, handleCreateChat, handleGetMessages } from "./routes/chats.js";
import {
  handleAdminStats, handleAdminGetUsers, handleAdminGetUser,
  handleAdminUpdateUser, handleAdminDeleteUser, handleAdminSetScam,
  handleAdminDeleteMessage, handleAdminDeleteAllMessages, handleAdminRevokeUserSessions,
} from "./routes/admin.js";

getDB(); // инициализируем БД при старте

function corsHeaders(origin: string): Record<string, string> {
  const ok = ALLOWED_ORIGINS.includes("*") || ALLOWED_ORIGINS.includes(origin);
  return {
    "Access-Control-Allow-Origin":  ok ? origin : "",
    "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Authorization",
    "Access-Control-Max-Age":       "86400",
  };
}

Bun.serve({
  port: PORT,
  async fetch(req: Request, srv: any) {
    const url    = new URL(req.url);
    const path   = url.pathname;
    const method = req.method;
    const origin = req.headers.get("origin") ?? "*";

    if (method === "OPTIONS")
      return new Response(null, { status: 204, headers: corsHeaders(origin) });

    if (path === "/ws") {
      if (srv.upgrade(req, { data: {} })) return undefined;
      return new Response("WebSocket upgrade failed", { status: 400 });
    }

    if (!checkHttpRateLimit(getClientIp(req)))
      return new Response(JSON.stringify({ error: "Too many requests" }), {
        status: 429, headers: { "Content-Type": "application/json", ...corsHeaders(origin) },
      });

    let res: Response;
    try { res = await route(req, method, path); }
    catch (e) {
      console.error("[SERVER]", e);
      res = new Response(JSON.stringify({ error: "Internal server error" }), {
        status: 500, headers: { "Content-Type": "application/json" },
      });
    }
    for (const [k, v] of Object.entries(corsHeaders(origin))) res.headers.set(k, v);
    return res;
  },
  websocket: websocketHandler,
  error(e) { console.error("[FATAL]", e); return new Response("Server error", { status: 500 }); },
});

async function route(req: Request, m: string, p: string): Promise<Response> {
  // Auth
  if (p === "/api/auth/register"  && m === "POST")   return handleRegister(req);
  if (p === "/api/auth/login"     && m === "POST")   return handleLogin(req);
  if (p === "/api/auth/refresh"   && m === "POST")   return handleRefresh(req);
  if (p === "/api/auth/logout"    && m === "DELETE") return handleLogout(req);
  // Keys
  if (p === "/api/keys/prekeys"   && m === "POST")   return handleUploadPrekeys(req);
  const pkM = p.match(/^\/api\/keys\/prekeys\/([^/]+)$/);
  if (pkM && m === "GET")  return handleGetPrekeys(req, pkM[1]);
  const bdM = p.match(/^\/api\/keys\/bundle\/([^/]+)$/);
  if (bdM && m === "GET")  return handleGetKeyBundle(req, bdM[1]);
  // Users
  if (p === "/api/users/search"   && m === "GET")    return handleSearchUsers(req);
  const uM = p.match(/^\/api\/users\/([^/]+)$/);
  if (uM && m === "GET")   return handleGetUser(req, uM[1]);
  // Chats
  if (p === "/api/chats"          && m === "GET")    return handleGetChats(req);
  if (p === "/api/chats"          && m === "POST")   return handleCreateChat(req);
  const msM = p.match(/^\/api\/chats\/([^/]+)\/messages$/);
  if (msM && m === "GET")  return handleGetMessages(req, msM[1]);
  // Admin
  if (p === "/api/admin/stats"    && m === "GET")    return handleAdminStats(req);
  if (p === "/api/admin/users"    && m === "GET")    return handleAdminGetUsers(req);
  const auM = p.match(/^\/api\/admin\/users\/([^/]+)$/);
  if (auM) {
    if (m === "GET")    return handleAdminGetUser(req, auM[1]);
    if (m === "PUT")    return handleAdminUpdateUser(req, auM[1]);
    if (m === "DELETE") return handleAdminDeleteUser(req, auM[1]);
  }
  const scM = p.match(/^\/api\/admin\/users\/([^/]+)\/scam$/);
  if (scM && m === "POST")   return handleAdminSetScam(req, scM[1]);
  const ssM = p.match(/^\/api\/admin\/users\/([^/]+)\/sessions$/);
  if (ssM && m === "DELETE") return handleAdminRevokeUserSessions(req, ssM[1]);
  const dmM = p.match(/^\/api\/admin\/messages\/([^/]+)$/);
  if (dmM && m === "DELETE") return handleAdminDeleteMessage(req, dmM[1]);
  const daM = p.match(/^\/api\/admin\/chats\/([^/]+)\/messages$/);
  if (daM && m === "DELETE") return handleAdminDeleteAllMessages(req, daM[1]);
  // Health
  if (p === "/health") return new Response(JSON.stringify({ status:"ok", ts:Date.now() }), { headers:{"Content-Type":"application/json"} });
  return new Response(JSON.stringify({ error: "Not found" }), { status: 404, headers: {"Content-Type":"application/json"} });
}

console.log(`\n🔐 QNS Server  •  port ${PORT}\n   WS: ws://localhost:${PORT}/ws\n`);
