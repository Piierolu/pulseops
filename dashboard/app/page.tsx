import { OperationsConsole } from "./operations-console";
import { redirect } from "next/navigation";
import { hasSession, isDemoMode } from "../lib/auth";

export const dynamic = "force-dynamic";

export default async function Home() {
  const demoMode = isDemoMode();
  if (!demoMode && !(await hasSession())) redirect("/api/auth/login?returnTo=/");
  const grafanaUrl = process.env.GRAFANA_URL ?? "http://localhost:3001/d/pulseops-overview/pulseops-overview";
  return <OperationsConsole grafanaUrl={grafanaUrl} demoMode={demoMode} />;
}
