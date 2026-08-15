import { validateConfiguration } from "../../../lib/auth";

export async function GET() {
  try {
    await validateConfiguration();
    return Response.json({ status: "ok" });
  } catch {
    return Response.json({ status: "unavailable" }, { status: 503 });
  }
}
