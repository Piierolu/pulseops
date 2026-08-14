"use client";

import { FormEvent, startTransition, useEffect, useState } from "react";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8082/api";
const GRAFANA_URL = process.env.NEXT_PUBLIC_GRAFANA_URL ?? "http://localhost:3001/d/pulseops-overview/pulseops-overview";

type Stats = {
  totalMonitors: number;
  upMonitors: number;
  degradedMonitors: number;
  downMonitors: number;
  pendingMonitors: number;
  openIncidents: number;
  totalAgents: number;
  onlineAgents: number;
  checks24h: number;
  availability24h: number | null;
  averageLatencyMs: number;
};

type MonitorSnapshot = {
  id: string;
  name: string;
  type: "HTTP" | "TCP" | "DNS" | "TLS";
  target: string;
  status: string;
  lastCheckStatus: string | null;
  lastLatencyMs: number | null;
  lastCheckedAt: string | null;
  availability24h: number | null;
};

type Overview = {
  stats: Stats;
  monitors: MonitorSnapshot[];
  generatedAt: string;
};

type Incident = {
  id: string;
  monitorId: string;
  status: "OPEN" | "RESOLVED";
  cause: string;
  openedAt: string;
  resolvedAt: string | null;
};

type Agent = {
  agentId: string;
  location: string;
  version: string;
  status: "ONLINE" | "OFFLINE";
  firstSeenAt: string;
  lastSeenAt: string;
};

export function OperationsConsole() {
  const [overview, setOverview] = useState<Overview | null>(null);
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [agents, setAgents] = useState<Agent[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [creating, setCreating] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);
  const [monitorType, setMonitorType] = useState<MonitorSnapshot["type"]>("HTTP");

  useEffect(() => {
    let active = true;

    async function load() {
      try {
        const [overviewResponse, incidentsResponse, agentsResponse] = await Promise.all([
          fetch(`${API_URL}/overview`, { cache: "no-store" }),
          fetch(`${API_URL}/incidents`, { cache: "no-store" }),
          fetch(`${API_URL}/agents`, { cache: "no-store" }),
        ]);
        if (!overviewResponse.ok || !incidentsResponse.ok || !agentsResponse.ok) {
          throw new Error("El control plane respondio con un error");
        }
        const nextOverview = (await overviewResponse.json()) as Overview;
        const nextIncidents = (await incidentsResponse.json()) as Incident[];
        const nextAgents = (await agentsResponse.json()) as Agent[];
        if (active) {
          startTransition(() => {
            setOverview(nextOverview);
            setIncidents(nextIncidents.slice(0, 8));
            setAgents(nextAgents);
            setError(null);
          });
        }
      } catch (loadError) {
        if (active) {
          setError(loadError instanceof Error ? loadError.message : "No se pudo cargar PulseOps");
        }
      }
    }

    void load();
    const interval = window.setInterval(load, 10_000);
    return () => {
      active = false;
      window.clearInterval(interval);
    };
  }, [refreshKey]);

  async function createMonitor(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setCreating(true);
    const form = new FormData(event.currentTarget);
    const payload: Record<string, string | number | null> = {
      name: String(form.get("name")),
      type: monitorType,
      frequencySeconds: Number(form.get("frequencySeconds")),
      timeoutMs: Number(form.get("timeoutMs")),
    };
    if (monitorType === "HTTP") {
      payload.targetUrl = String(form.get("targetUrl"));
      payload.expectedStatus = Number(form.get("expectedStatus"));
    } else {
      payload.host = String(form.get("host"));
    }
    if (monitorType === "TCP" || monitorType === "TLS") {
      payload.port = Number(form.get("port"));
    }
    if (monitorType === "DNS") {
      payload.dnsRecordType = String(form.get("dnsRecordType"));
      payload.expectedValue = String(form.get("expectedValue") ?? "") || null;
    }
    if (monitorType === "TLS") {
      payload.tlsExpiryWarningDays = Number(form.get("tlsExpiryWarningDays"));
    }

    try {
      const response = await fetch(`${API_URL}/monitors`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      if (!response.ok) {
        const body = (await response.json()) as { message?: string };
        throw new Error(body.message ?? "No se pudo crear el monitor");
      }
      event.currentTarget.reset();
      setMonitorType("HTTP");
      setShowCreate(false);
      setRefreshKey((current) => current + 1);
    } catch (createError) {
      setError(createError instanceof Error ? createError.message : "No se pudo crear el monitor");
    } finally {
      setCreating(false);
    }
  }

  const stats = overview?.stats;
  const monitorNames = new Map(overview?.monitors.map((monitor) => [monitor.id, monitor.name]));
  const platformState = !overview
    ? "CONNECTING"
    : stats?.downMonitors || stats?.degradedMonitors
      ? "ATTENTION"
      : "NOMINAL";

  return (
    <main className="shell">
      <header className="topbar">
        <div className="brandBlock">
          <div className="brandMark" aria-hidden="true"><span /><span /><span /></div>
          <div>
            <p className="eyebrow">SYNTHETIC OPERATIONS NETWORK</p>
            <h1>PULSE<span>/OPS</span></h1>
          </div>
        </div>
        <div className="topActions">
          <a className="observabilityLink" href={GRAFANA_URL} target="_blank" rel="noreferrer">OBSERVABILITY</a>
          <div className={`networkState ${platformState.toLowerCase()}`}>
            <span className="stateDot" />
            NETWORK {platformState}
          </div>
          <button className="primaryButton" onClick={() => setShowCreate((current) => !current)}>
            {showCreate ? "CLOSE" : "+ NEW MONITOR"}
          </button>
        </div>
      </header>

      {error && <div className="errorBanner"><strong>CONTROL PLANE</strong>{error}</div>}

      {showCreate && (
        <section className="createPanel">
          <div>
            <p className="sectionCode">CONFIG / {monitorType}</p>
            <h2>Deploy a new probe</h2>
            <p>Quartz enviara la primera ejecucion al agente disponible inmediatamente.</p>
          </div>
          <form onSubmit={createMonitor}>
            <label>NAME<input name="name" required maxLength={120} placeholder="Public API" /></label>
            <label>PROTOCOL<select value={monitorType} onChange={(event) => setMonitorType(event.target.value as MonitorSnapshot["type"])}><option>HTTP</option><option>TCP</option><option>DNS</option><option>TLS</option></select></label>
            {monitorType === "HTTP" ? (
              <label className="wideField">TARGET URL<input name="targetUrl" required type="url" placeholder="https://example.com/health" /></label>
            ) : (
              <label className="wideField">HOST<input name="host" required placeholder={monitorType === "DNS" ? "example.com" : "service.internal"} /></label>
            )}
            {(monitorType === "TCP" || monitorType === "TLS") && <label>PORT<input name="port" required type="number" min="1" max="65535" defaultValue={monitorType === "TLS" ? "443" : "5432"} /></label>}
            {monitorType === "DNS" && <label>RECORD<select name="dnsRecordType" defaultValue="A"><option>A</option><option>AAAA</option><option>CNAME</option><option>TXT</option></select></label>}
            {monitorType === "DNS" && <label className="wideField">EXPECTED VALUE<input name="expectedValue" placeholder="Optional exact value" /></label>}
            {monitorType === "TLS" && <label>WARN BEFORE<input name="tlsExpiryWarningDays" required type="number" min="1" max="365" defaultValue="30" /></label>}
            <label>INTERVAL<input name="frequencySeconds" required type="number" min="10" defaultValue="30" /></label>
            <label>TIMEOUT MS<input name="timeoutMs" required type="number" min="100" defaultValue="5000" /></label>
            {monitorType === "HTTP" && <label>EXPECTED HTTP<input name="expectedStatus" required type="number" min="100" max="599" defaultValue="200" /></label>}
            <button className="deployButton" disabled={creating}>{creating ? "DEPLOYING..." : "DEPLOY PROBE"}</button>
          </form>
        </section>
      )}

      <section className="metricGrid" aria-label="Metricas globales">
        <Metric label="GLOBAL UPTIME / 24H" value={formatAvailability(stats?.availability24h)} detail={`${stats?.checks24h ?? 0} checks sampled`} tone="green" />
        <Metric label="ACTIVE INCIDENTS" value={String(stats?.openIncidents ?? 0).padStart(2, "0")} detail={`${stats?.downMonitors ?? 0} services down`} tone={stats?.openIncidents ? "orange" : "neutral"} />
        <Metric label="MEAN RESPONSE" value={`${Math.round(stats?.averageLatencyMs ?? 0)} ms`} detail="all locations / 24h" tone="neutral" />
        <Metric label="AGENT MESH" value={`${stats?.onlineAgents ?? 0}/${stats?.totalAgents ?? 0}`} detail={`${stats?.totalMonitors ?? 0} monitors deployed`} tone={stats?.onlineAgents ? "neutral" : "orange"} />
      </section>

      <div className="contentGrid">
        <section className="monitorPanel">
          <div className="sectionHeader">
            <div><p className="sectionCode">LIVE / SERVICE MATRIX</p><h2>Monitor network</h2></div>
            <p className="lastSync">SYNC {formatClock(overview?.generatedAt)}</p>
          </div>
          <div className="monitorLabels" aria-hidden="true">
            <span>SERVICE / ENDPOINT</span><span>STATE</span><span>LATENCY</span><span>UPTIME 24H</span>
          </div>
          <div className="monitorList">
            {!overview && <EmptyState text="Establishing telemetry link..." />}
            {overview?.monitors.length === 0 && <EmptyState text="No probes deployed. Create the first monitor." />}
            {overview?.monitors.map((monitor) => <MonitorRow key={monitor.id} monitor={monitor} />)}
          </div>
        </section>

        <aside className="incidentPanel">
          <div className="sectionHeader">
            <div><p className="sectionCode">EVENT STREAM</p><h2>Incidents</h2></div>
            <span className="eventCount">{incidents.length}</span>
          </div>
          <div className="incidentList">
            {incidents.length === 0 && <EmptyState text="No incident history." />}
            {incidents.map((incident) => (
              <article className="incidentItem" key={incident.id}>
                <div className={`incidentGlyph ${incident.status.toLowerCase()}`}>{incident.status === "OPEN" ? "!" : "OK"}</div>
                <div>
                  <div className="incidentTitle">
                    <strong>{monitorNames.get(incident.monitorId) ?? "Unknown monitor"}</strong>
                    <span>{incident.status}</span>
                  </div>
                  <p>{incident.cause}</p>
                  <time>{formatDate(incident.openedAt)}</time>
                </div>
              </article>
            ))}
          </div>
        </aside>
      </div>

      <section className="agentPanel">
        <div className="sectionHeader">
          <div><p className="sectionCode">DISTRIBUTED / EXECUTORS</p><h2>Agent mesh</h2></div>
          <span className="eventCount">{agents.length}</span>
        </div>
        <div className="agentList">
          {agents.length === 0 && <EmptyState text="Awaiting the first agent heartbeat." />}
          {agents.map((agent) => (
            <article className="agentItem" key={agent.agentId}>
              <span className={`agentBeacon ${agent.status.toLowerCase()}`} />
              <div><strong>{agent.agentId}</strong><code>{agent.location}</code></div>
              <span className={`statusTag ${agent.status === "ONLINE" ? "up" : "down"}`}>{agent.status}</span>
              <div className="agentMeta"><span>v{agent.version}</span><time>{formatDate(agent.lastSeenAt)}</time></div>
            </article>
          ))}
        </div>
      </section>

      <footer>
        <span>PULSEOPS CONTROL PLANE // BUILD 0.3</span>
        <span>HTTP · TCP · DNS · TLS</span>
      </footer>
    </main>
  );
}

function Metric({ label, value, detail, tone }: { label: string; value: string; detail: string; tone: string }) {
  return <article className={`metricCard ${tone}`}><p>{label}</p><strong>{value}</strong><span>{detail}</span></article>;
}

function MonitorRow({ monitor }: { monitor: MonitorSnapshot }) {
  const availability = monitor.availability24h ?? 0;
  return (
    <article className="monitorRow">
      <div className="monitorIdentity"><span className={`servicePulse ${monitor.status.toLowerCase()}`} /><div><strong>{monitor.name}<span className="protocolTag">{monitor.type}</span></strong><code>{monitor.target}</code></div></div>
      <div><span className={`statusTag ${monitor.status.toLowerCase()}`}>{monitor.status}</span></div>
      <div className="latencyValue"><strong>{monitor.lastLatencyMs ?? "--"}</strong><span> ms</span></div>
      <div className="availabilityCell"><strong>{formatAvailability(monitor.availability24h)}</strong><div className="availabilityTrack"><span style={{ width: `${availability}%` }} /></div><time>{monitor.lastCheckedAt ? formatDate(monitor.lastCheckedAt) : "awaiting first result"}</time></div>
    </article>
  );
}

function EmptyState({ text }: { text: string }) {
  return <div className="emptyState"><span>~</span><p>{text}</p></div>;
}

function formatAvailability(value: number | null | undefined) {
  return value == null ? "--.--%" : `${value.toFixed(2)}%`;
}

function formatClock(value: string | undefined) {
  if (!value) return "--:--:--";
  return new Intl.DateTimeFormat("en-GB", { hour: "2-digit", minute: "2-digit", second: "2-digit" }).format(new Date(value));
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en-GB", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" }).format(new Date(value)).toUpperCase();
}
