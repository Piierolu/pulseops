"use client";

import { FormEvent, startTransition, useEffect, useState } from "react";

const API_URL = "/api/control-plane";

type MonitorType = "HTTP" | "TCP" | "DNS" | "TLS";

type MonitorDetails = {
  id: string;
  projectId: string;
  name: string;
  type: MonitorType;
  targetUrl: string | null;
  host: string | null;
  port: number | null;
  dnsRecordType: string | null;
  expectedValue: string | null;
  tlsExpiryWarningDays: number | null;
  frequencySeconds: number;
  timeoutMs: number;
  expectedStatus: number | null;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
  archivedAt: string | null;
  lifecycleVersion: number;
};

type CheckResult = {
  id: string;
  executionId: string;
  agentId: string;
  location: string;
  status: "SUCCESS" | "FAILURE";
  latencyMs: number;
  statusCode: number | null;
  error: string | null;
  details: Record<string, unknown>;
  checkedAt: string;
};

export function MonitorDetailPanel({ projectId, monitorId, canEdit, onClose, onChanged }: {
  projectId: string;
  monitorId: string;
  canEdit: boolean;
  onClose: () => void;
  onChanged: () => void;
}) {
  const [monitor, setMonitor] = useState<MonitorDetails | null>(null);
  const [results, setResults] = useState<CheckResult[]>([]);
  const [editing, setEditing] = useState(false);
  const [editType, setEditType] = useState<MonitorType>("HTTP");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let active = true;

    async function load() {
      setError(null);
      try {
        const [monitorResponse, resultsResponse] = await Promise.all([
          fetch(`${API_URL}/projects/${projectId}/monitors/${monitorId}`, { cache: "no-store" }),
          fetch(`${API_URL}/projects/${projectId}/monitors/${monitorId}/results?limit=50`, { cache: "no-store" }),
        ]);
        if ([monitorResponse, resultsResponse].some((response) => response.status === 401)) {
          window.location.assign("/api/auth/login?returnTo=/");
          return;
        }
        if (!monitorResponse.ok || !resultsResponse.ok) {
          throw new Error("No se pudo cargar el detalle del monitor");
        }
        const nextMonitor = (await monitorResponse.json()) as MonitorDetails;
        const nextResults = (await resultsResponse.json()) as CheckResult[];
        if (active) startTransition(() => {
          setMonitor(nextMonitor);
          setResults(nextResults);
          setEditType(nextMonitor.type);
        });
      } catch (loadError) {
        if (active) setError(errorMessage(loadError));
      }
    }

    void load();
    return () => {
      active = false;
    };
  }, [projectId, monitorId, reloadKey]);

  async function lifecycle(action: "pause" | "resume" | "restore" | "archive") {
    if (action === "archive" && !window.confirm("Archive this monitor? Scheduling stops but history is retained.")) return;
    setBusy(true);
    setError(null);
    const path = action === "archive" ? "" : `/${action}`;
    try {
      const response = await fetch(`${API_URL}/projects/${projectId}/monitors/${monitorId}${path}`, {
        method: action === "archive" ? "DELETE" : "POST",
      });
      if (!response.ok) throw new Error(await responseMessage(response, `No se pudo ejecutar ${action}`));
      setEditing(false);
      setReloadKey((current) => current + 1);
      onChanged();
    } catch (actionError) {
      setError(errorMessage(actionError));
    } finally {
      setBusy(false);
    }
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const payload: Record<string, string | number | null> = {
      name: String(form.get("name")),
      type: editType,
      frequencySeconds: Number(form.get("frequencySeconds")),
      timeoutMs: Number(form.get("timeoutMs")),
    };
    if (editType === "HTTP") {
      payload.targetUrl = String(form.get("targetUrl"));
      payload.expectedStatus = Number(form.get("expectedStatus"));
    } else {
      payload.host = String(form.get("host"));
    }
    if (editType === "TCP" || editType === "TLS") payload.port = Number(form.get("port"));
    if (editType === "DNS") {
      payload.dnsRecordType = String(form.get("dnsRecordType"));
      payload.expectedValue = String(form.get("expectedValue") ?? "") || null;
    }
    if (editType === "TLS") payload.tlsExpiryWarningDays = Number(form.get("tlsExpiryWarningDays"));

    setBusy(true);
    setError(null);
    try {
      const response = await fetch(`${API_URL}/projects/${projectId}/monitors/${monitorId}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      if (!response.ok) throw new Error(await responseMessage(response, "No se pudo actualizar el monitor"));
      const updated = (await response.json()) as MonitorDetails;
      setMonitor(updated);
      setEditing(false);
      setReloadKey((current) => current + 1);
      onChanged();
    } catch (saveError) {
      setError(errorMessage(saveError));
    } finally {
      setBusy(false);
    }
  }

  const lifecycleState = monitor?.archivedAt ? "ARCHIVED" : monitor?.enabled ? "ACTIVE" : "PAUSED";

  return (
    <aside className="monitorDetailPanel">
      <div className="sectionHeader detailHeader">
        <div><p className="sectionCode">MONITOR / {lifecycleState}</p><h2>{monitor?.name ?? "Loading monitor..."}</h2></div>
        <button className="panelClose" type="button" onClick={onClose}>CLOSE</button>
      </div>

      {error && <div className="detailError">{error}</div>}

      {monitor && !editing && (
        <>
          <div className="detailFacts">
            <DetailFact label="PROTOCOL" value={monitor.type} />
            <DetailFact label="TARGET" value={formatTarget(monitor)} wide />
            <DetailFact label="INTERVAL" value={`${monitor.frequencySeconds}s`} />
            <DetailFact label="TIMEOUT" value={`${monitor.timeoutMs}ms`} />
            {monitor.expectedStatus && <DetailFact label="EXPECTED HTTP" value={String(monitor.expectedStatus)} />}
            {monitor.dnsRecordType && <DetailFact label="DNS RECORD" value={monitor.dnsRecordType} />}
            {monitor.expectedValue && <DetailFact label="EXPECTED VALUE" value={monitor.expectedValue} wide />}
            {monitor.tlsExpiryWarningDays && <DetailFact label="CERT WARNING" value={`${monitor.tlsExpiryWarningDays} days`} />}
            <DetailFact label="UPDATED" value={formatDate(monitor.updatedAt)} wide />
          </div>

          {canEdit && (
            <div className="monitorActions">
              {!monitor.archivedAt && <button disabled={busy} onClick={() => setEditing(true)}>EDIT</button>}
              {!monitor.archivedAt && monitor.enabled && <button disabled={busy} onClick={() => void lifecycle("pause")}>PAUSE</button>}
              {!monitor.archivedAt && !monitor.enabled && <button className="positive" disabled={busy} onClick={() => void lifecycle("resume")}>RESUME</button>}
              {monitor.archivedAt && <button className="positive" disabled={busy} onClick={() => void lifecycle("restore")}>RESTORE AS PAUSED</button>}
              {!monitor.archivedAt && <button className="danger" disabled={busy} onClick={() => void lifecycle("archive")}>ARCHIVE</button>}
            </div>
          )}
          {!canEdit && <p className="readOnlyLabel">READ ONLY / VIEWER</p>}
        </>
      )}

      {monitor && editing && (
        <form className="monitorEditForm" key={monitor.lifecycleVersion} onSubmit={save}>
          <label>NAME<input name="name" required maxLength={120} defaultValue={monitor.name} /></label>
          <label>PROTOCOL<select value={editType} onChange={(event) => setEditType(event.target.value as MonitorType)}><option>HTTP</option><option>TCP</option><option>DNS</option><option>TLS</option></select></label>
          {editType === "HTTP" ? (
            <label className="wideField">TARGET URL<input name="targetUrl" required type="url" defaultValue={monitor.targetUrl ?? ""} /></label>
          ) : (
            <label className="wideField">HOST<input name="host" required defaultValue={monitor.host ?? ""} /></label>
          )}
          {(editType === "TCP" || editType === "TLS") && <label>PORT<input name="port" required type="number" min="1" max="65535" defaultValue={monitor.port ?? (editType === "TLS" ? 443 : 80)} /></label>}
          {editType === "DNS" && <label>RECORD<select name="dnsRecordType" defaultValue={monitor.dnsRecordType ?? "A"}><option>A</option><option>AAAA</option><option>CNAME</option><option>TXT</option></select></label>}
          {editType === "DNS" && <label className="wideField">EXPECTED VALUE<input name="expectedValue" defaultValue={monitor.expectedValue ?? ""} /></label>}
          {editType === "TLS" && <label>WARN BEFORE<input name="tlsExpiryWarningDays" required type="number" min="1" max="365" defaultValue={monitor.tlsExpiryWarningDays ?? 30} /></label>}
          <label>INTERVAL<input name="frequencySeconds" required type="number" min="10" max="86400" defaultValue={monitor.frequencySeconds} /></label>
          <label>TIMEOUT MS<input name="timeoutMs" required type="number" min="100" max="60000" defaultValue={monitor.timeoutMs} /></label>
          {editType === "HTTP" && <label>EXPECTED HTTP<input name="expectedStatus" required type="number" min="100" max="599" defaultValue={monitor.expectedStatus ?? 200} /></label>}
          <div className="editActions"><button type="button" onClick={() => setEditing(false)}>CANCEL</button><button className="positive" disabled={busy}>{busy ? "SAVING..." : "SAVE"}</button></div>
        </form>
      )}

      <div className="historyHeader"><p className="sectionCode">RECENT CHECKS / {results.length}</p></div>
      <div className="resultHistory">
        {results.length === 0 && <div className="memberEmpty">No check history.</div>}
        {results.map((result) => (
          <article className="resultItem" key={result.executionId}>
            <div className="resultTitle"><strong className={result.status.toLowerCase()}>{result.status}</strong><time>{formatDate(result.checkedAt)}</time></div>
            <p>{result.location} / {result.latencyMs}ms{result.statusCode ? ` / HTTP ${result.statusCode}` : ""}</p>
            {result.error && <code>{result.error}</code>}
            {Object.keys(result.details).length > 0 && <details><summary>PROTOCOL DATA</summary><pre>{JSON.stringify(result.details, null, 2)}</pre></details>}
          </article>
        ))}
      </div>
    </aside>
  );
}

function DetailFact({ label, value, wide = false }: { label: string; value: string; wide?: boolean }) {
  return <div className={wide ? "detailFact wide" : "detailFact"}><span>{label}</span><strong>{value}</strong></div>;
}

function formatTarget(monitor: MonitorDetails) {
  if (monitor.targetUrl) return monitor.targetUrl;
  return monitor.port ? `${monitor.host}:${monitor.port}` : monitor.host ?? "--";
}

async function responseMessage(response: Response, fallback: string) {
  try {
    const body = (await response.json()) as { message?: string; error?: string };
    return body.message ?? body.error ?? fallback;
  } catch {
    return fallback;
  }
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : "No se pudo completar la operacion";
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en-GB", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit", second: "2-digit" }).format(new Date(value)).toUpperCase();
}
