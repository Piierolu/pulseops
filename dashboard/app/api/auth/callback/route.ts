import { NextRequest, NextResponse } from "next/server";
import { completeLogin } from "../../../../lib/auth";

export async function GET(request: NextRequest) {
  try {
    const returnTo = await completeLogin(request.nextUrl);
    return NextResponse.redirect(new URL(returnTo, request.url));
  } catch (error) {
    console.error("OIDC callback failed", error);
    return NextResponse.redirect(new URL("/auth/error", request.url));
  }
}
