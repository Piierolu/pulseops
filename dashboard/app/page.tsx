import { OperationsConsole } from "./operations-console";

export const dynamic = "force-dynamic";

export default function Home() {
  const grafanaUrl = process.env.GRAFANA_URL ?? "http://localhost:3001/d/pulseops-overview/pulseops-overview";
  return <OperationsConsole grafanaUrl={grafanaUrl} />;
}
