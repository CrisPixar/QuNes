import { verifyToken, type JwtPayload } from "../utils/jwt.js";

export interface AuthContext { userId: string; username: string; role: string; }

export function extractAuth(req: Request): AuthContext | null {
  const h = req.headers.get("authorization");
  if (!h?.startsWith("Bearer ")) return null;
  try {
    const p = verifyToken(h.slice(7));
    return { userId: p.sub, username: p.username, role: p.role };
  } catch { return null; }
}

export function requireAuth(req: Request): AuthContext | Response {
  const ctx = extractAuth(req);
  return ctx ?? json({ error: "Unauthorized" }, 401);
}

export function requireAdmin(req: Request): AuthContext | Response {
  const ctx = extractAuth(req);
  if (!ctx) return json({ error: "Unauthorized" }, 401);
  if (ctx.role !== "admin") return json({ error: "Forbidden: admin role required" }, 403);
  return ctx;
}

export function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
