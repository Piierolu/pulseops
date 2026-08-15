import { NextRequest, NextResponse } from "next/server";
import { clearSession, isTrustedOrigin } from "../../../../lib/auth";

export async function POST(request: NextRequest) {
  if (!isTrustedOrigin(request.headers.get("origin"))) {
    return Response.json({ error: "Invalid request origin" }, { status: 403 });
  }
  await clearSession();
  return NextResponse.redirect(new URL("/auth/signed-out", request.url), 303);
}
