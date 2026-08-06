import { verifyToken, type JwtPayload } from "../utils/jwt.js";

export interface AuthContext {
  userId: string;
  username: string;
  role: string;
}

export function extractAuth(req: Request): AuthContext | null {
  const header = req.headers.get("authorization");
  if (!header?.startsWith("Bearer ")) return null;
  try {
    const payload = verifyToken(header.slice(7).trim(), "access");
    return { userId: payload.sub, username: payload.username, role: payload.role };
  } catch {
    return null;
  }
}

export function requireAuth(req: Request): AuthContext | Response {
  return extractAuth(req) ?? json({ error: "Unauthorized", code: "AUTH_REQUIRED" }, 401);
}

export function requireAdmin(req: Request): AuthContext | Response {
  const context = extractAuth(req);
  if (!context) return json({ error: "Unauthorized", code: "AUTH_REQUIRED" }, 401);
  if (context.role !== "admin") return json({ error: "Forbidden: admin role required", code: "ADMIN_REQUIRED" }, 403);
  return context;
}

export function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}
