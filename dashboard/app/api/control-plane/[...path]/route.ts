import { NextRequest } from "next/server";
import { accessToken, isDemoMode, isTrustedOrigin } from "../../../../lib/auth";

const CONTROL_PLANE_URL = process.env.CONTROL_PLANE_INTERNAL_URL ?? "http://localhost:8082/api";
const SAFE_PATH_SEGMENT = /^[A-Za-z0-9_-]+$/;

type RouteContext = {
  params: Promise<{ path: string[] }>;
};

async function proxy(request: NextRequest, context: RouteContext) {
  const { path } = await context.params;
  if (path.length === 0 || path.some((segment) => !SAFE_PATH_SEGMENT.test(segment))) {
    return Response.json({ error: "Invalid control-plane path" }, { status: 400 });
  }
  if (!["GET", "HEAD"].includes(request.method) && !isTrustedOrigin(request.headers.get("origin"))) {
    return Response.json({ error: "Invalid request origin" }, { status: 403 });
  }

  const base = new URL(`${CONTROL_PLANE_URL.replace(/\/$/, "")}/`);
  const target = new URL(path.map(encodeURIComponent).join("/"), base);
  if (target.origin !== base.origin || !target.pathname.startsWith(base.pathname)) {
    return Response.json({ error: "Invalid control-plane path" }, { status: 400 });
  }
  target.search = request.nextUrl.search;

  const headers = new Headers();
  for (const name of ["accept", "content-type"]) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }
  if (!isDemoMode()) {
    try {
      headers.set("authorization", `Bearer ${await accessToken()}`);
    } catch {
      return Response.json({ error: "Authentication required" }, { status: 401 });
    }
  }
  const response = await fetch(target, {
    method: request.method,
    headers,
    body: request.method === "GET" || request.method === "HEAD" ? undefined : await request.arrayBuffer(),
    cache: "no-store",
  });
  const responseHeaders = new Headers();
  const contentType = response.headers.get("content-type");
  if (contentType) responseHeaders.set("content-type", contentType);
  return new Response(response.body, {
    status: response.status,
    headers: responseHeaders,
  });
}

export const GET = proxy;
export const POST = proxy;
export const PUT = proxy;
export const PATCH = proxy;
export const DELETE = proxy;
