import "server-only";

import { createHash } from "node:crypto";
import { cookies } from "next/headers";
import { EncryptJWT, jwtDecrypt, type JWTPayload } from "jose";
import * as oidc from "openid-client";

const SESSION_COOKIE = "pulseops.session";
const LOGIN_COOKIE = "pulseops.login";

type LoginState = JWTPayload & {
  state: string;
  nonce: string;
  verifier: string;
  returnTo: string;
};

type Session = JWTPayload & {
  accessToken: string;
  accessTokenExpiresAt: number;
  subject: string;
  name?: string;
  email?: string;
};

let configuration: Promise<oidc.Configuration> | undefined;

export function isDemoMode() {
  return securityMode() === "demo";
}

export function isTrustedOrigin(origin: string | null) {
  if (!origin) return false;
  try {
    return new URL(origin).origin === new URL(required("DASHBOARD_PUBLIC_URL")).origin;
  } catch {
    return false;
  }
}

export async function createLoginUrl(returnTo: string) {
  const config = await oidcConfiguration();
  const verifier = oidc.randomPKCECodeVerifier();
  const challenge = await oidc.calculatePKCECodeChallenge(verifier);
  const state = oidc.randomState();
  const nonce = oidc.randomNonce();
  const safeReturnTo = normalizeReturnTo(returnTo);
  const loginState: LoginState = { state, nonce, verifier, returnTo: safeReturnTo };
  const cookieStore = await cookies();
  cookieStore.set(LOGIN_COOKIE, await encrypt(loginState, "10m"), cookieOptions(600));
  const audience = process.env.OIDC_AUDIENCE;
  return oidc.buildAuthorizationUrl(config, {
    redirect_uri: callbackUrl(),
    scope: "openid profile email",
    code_challenge: challenge,
    code_challenge_method: "S256",
    state,
    nonce,
    ...(audience ? { audience } : {}),
  });
}

export async function completeLogin(currentUrl: URL) {
  const cookieStore = await cookies();
  const encodedState = cookieStore.get(LOGIN_COOKIE)?.value;
  if (!encodedState) throw new Error("OIDC login state is missing or expired");
  const login = await decrypt<LoginState>(encodedState, "pulseops-login");
  const tokens = await oidc.authorizationCodeGrant(await oidcConfiguration(), currentUrl, {
    pkceCodeVerifier: login.verifier,
    expectedState: login.state,
    expectedNonce: login.nonce,
    idTokenExpected: true,
  });
  if (!tokens.access_token) throw new Error("OIDC provider did not return an access token");
  const claims = tokens.claims();
  if (!claims?.sub) throw new Error("OIDC provider did not return a subject");
  const session: Session = {
    accessToken: tokens.access_token,
    accessTokenExpiresAt: Math.floor(Date.now() / 1000) + (tokens.expiresIn() ?? 300),
    subject: claims.sub,
    name: typeof claims.name === "string" ? claims.name : undefined,
    email: typeof claims.email === "string" ? claims.email : undefined,
  };
  const maxAge = Math.max(1, session.accessTokenExpiresAt - Math.floor(Date.now() / 1000));
  cookieStore.set(SESSION_COOKIE, await encrypt(session, session.accessTokenExpiresAt), cookieOptions(maxAge));
  cookieStore.delete(LOGIN_COOKIE);
  return normalizeReturnTo(login.returnTo);
}

export async function hasSession() {
  if (isDemoMode()) return true;
  const encoded = (await cookies()).get(SESSION_COOKIE)?.value;
  if (!encoded) return false;
  try {
    await decrypt<Session>(encoded, "pulseops-session");
    return true;
  } catch {
    return false;
  }
}

export async function accessToken() {
  if (isDemoMode()) return undefined;
  const cookieStore = await cookies();
  const encoded = cookieStore.get(SESSION_COOKIE)?.value;
  if (!encoded) throw new Error("Authentication required");
  const session = await decrypt<Session>(encoded, "pulseops-session");
  const now = Math.floor(Date.now() / 1000);
  if (session.accessTokenExpiresAt <= now) throw new Error("OIDC session expired");
  return session.accessToken;
}

export async function clearSession() {
  const cookieStore = await cookies();
  cookieStore.delete(SESSION_COOKIE);
  cookieStore.delete(LOGIN_COOKIE);
}

export async function validateConfiguration() {
  if (isDemoMode()) return;
  required("OIDC_ISSUER_URI");
  required("OIDC_CLIENT_ID");
  required("OIDC_CLIENT_SECRET");
  required("DASHBOARD_PUBLIC_URL");
  encryptionKey();
  await oidcConfiguration();
}

function securityMode() {
  const mode = process.env.SECURITY_MODE ?? "oidc";
  if (mode !== "oidc" && mode !== "demo") throw new Error("SECURITY_MODE must be oidc or demo");
  return mode;
}

function callbackUrl() {
  const base = required("DASHBOARD_PUBLIC_URL").replace(/\/$/, "");
  return `${base}/api/auth/callback`;
}

function oidcConfiguration() {
  if (isDemoMode()) throw new Error("OIDC is disabled in demo mode");
  configuration ??= oidc.discovery(
    new URL(required("OIDC_ISSUER_URI")),
    required("OIDC_CLIENT_ID"),
    required("OIDC_CLIENT_SECRET"),
    oidc.ClientSecretBasic(required("OIDC_CLIENT_SECRET")),
  ).catch((error) => {
    configuration = undefined;
    throw error;
  });
  return configuration;
}

async function encrypt(payload: JWTPayload, expiration: string | number) {
  return new EncryptJWT(payload)
    .setProtectedHeader({ alg: "dir", enc: "A256GCM" })
    .setIssuer("pulseops-dashboard")
    .setAudience(payload.state ? "pulseops-login" : "pulseops-session")
    .setIssuedAt()
    .setExpirationTime(expiration)
    .encrypt(encryptionKey());
}

function normalizeReturnTo(value: string) {
  try {
    const base = new URL(required("DASHBOARD_PUBLIC_URL"));
    const resolved = new URL(value, base);
    if (resolved.origin !== base.origin) return "/";
    return `${resolved.pathname}${resolved.search}${resolved.hash}`;
  } catch {
    return "/";
  }
}

async function decrypt<T extends JWTPayload>(token: string, audience: string) {
  const { payload } = await jwtDecrypt(token, encryptionKey(), {
    issuer: "pulseops-dashboard",
    audience,
  });
  return payload as T;
}

function encryptionKey() {
  const secret = required("AUTH_SECRET");
  if (secret.length < 32) throw new Error("AUTH_SECRET must contain at least 32 characters");
  return createHash("sha256").update(secret).digest();
}

function cookieOptions(maxAge: number) {
  return {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production" && !isDemoMode(),
    sameSite: "lax" as const,
    path: "/",
    maxAge,
  };
}

function required(name: string) {
  const value = process.env[name];
  if (!value) throw new Error(`${name} is required`);
  return value;
}
