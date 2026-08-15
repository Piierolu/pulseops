import { NextRequest, NextResponse } from "next/server";
import { createLoginUrl, isDemoMode } from "../../../../lib/auth";

export async function GET(request: NextRequest) {
  if (isDemoMode()) return NextResponse.redirect(new URL("/", request.url));
  const loginUrl = await createLoginUrl(request.nextUrl.searchParams.get("returnTo") ?? "/");
  return NextResponse.redirect(loginUrl);
}
