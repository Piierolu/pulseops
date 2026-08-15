"use client";

import { FormEvent, startTransition, useEffect, useState } from "react";

const API_URL = "/api/control-plane";
const ROLES = ["OWNER", "ADMIN", "EDITOR", "VIEWER"] as const;

type TeamRole = (typeof ROLES)[number];

type TeamMember = {
  id: string;
  subject: string;
  email: string | null;
  displayName: string | null;
  role: TeamRole;
  currentUser: boolean;
};

export function TeamMembersPanel({ teamId, canManage, onClose }: {
  teamId: string;
  canManage: boolean;
  onClose: () => void;
}) {
  const [members, setMembers] = useState<TeamMember[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyMember, setBusyMember] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function loadMembers() {
      try {
        const response = await fetch(`${API_URL}/teams/${teamId}/members`, { cache: "no-store" });
        if (response.status === 401) {
          window.location.assign("/api/auth/login?returnTo=/");
          return;
        }
        if (!response.ok) throw new Error(await responseMessage(response, "No se pudieron cargar los miembros"));
        const available = (await response.json()) as TeamMember[];
        if (active) startTransition(() => setMembers(available));
      } catch (loadError) {
        if (active) setError(errorMessage(loadError));
      } finally {
        if (active) setLoading(false);
      }
    }

    void loadMembers();
    return () => {
      active = false;
    };
  }, [teamId]);

  async function addMember(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    setAdding(true);
    setError(null);
    const form = new FormData(formElement);
    try {
      const response = await fetch(`${API_URL}/teams/${teamId}/members`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ subject: String(form.get("subject")), role: String(form.get("role")) }),
      });
      if (!response.ok) throw new Error(await responseMessage(response, "No se pudo agregar el miembro"));
      const created = (await response.json()) as TeamMember;
      setMembers((current) => [...current, created]);
      formElement.reset();
    } catch (addError) {
      setError(errorMessage(addError));
    } finally {
      setAdding(false);
    }
  }

  async function changeRole(member: TeamMember, role: TeamRole) {
    setBusyMember(member.id);
    setError(null);
    try {
      const response = await fetch(`${API_URL}/teams/${teamId}/members/${member.id}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ role }),
      });
      if (!response.ok) throw new Error(await responseMessage(response, "No se pudo actualizar el rol"));
      const updated = (await response.json()) as TeamMember;
      if (updated.currentUser) {
        window.location.reload();
        return;
      }
      setMembers((current) => current.map((item) => item.id === updated.id ? updated : item));
    } catch (updateError) {
      setError(errorMessage(updateError));
    } finally {
      setBusyMember(null);
    }
  }

  async function removeMember(member: TeamMember) {
    const label = member.displayName ?? member.email ?? member.subject;
    if (!window.confirm(`Remove ${label} from this team?`)) return;
    setBusyMember(member.id);
    setError(null);
    try {
      const response = await fetch(`${API_URL}/teams/${teamId}/members/${member.id}`, { method: "DELETE" });
      if (!response.ok) throw new Error(await responseMessage(response, "No se pudo eliminar el miembro"));
      if (member.currentUser) {
        window.location.reload();
        return;
      }
      setMembers((current) => current.filter((item) => item.id !== member.id));
    } catch (removeError) {
      setError(errorMessage(removeError));
    } finally {
      setBusyMember(null);
    }
  }

  return (
    <section className="membersPanel">
      <div className="membersHeading">
        <div>
          <p className="sectionCode">ACCESS / TEAM DIRECTORY</p>
          <h2>Identity assignments</h2>
          <p>Los permisos del equipo se heredan en todos sus proyectos.</p>
        </div>
        <button className="panelClose" type="button" onClick={onClose}>CLOSE</button>
      </div>

      {error && <div className="membersError">{error}</div>}

      {canManage && (
        <form className="memberInvite" onSubmit={addMember}>
          <label>OIDC SUBJECT<input name="subject" required maxLength={512} placeholder="auth-provider-user-id" /></label>
          <label>ROLE<select name="role" defaultValue="VIEWER">{ROLES.map((role) => <option key={role}>{role}</option>)}</select></label>
          <button className="deployButton" disabled={adding}>{adding ? "ASSIGNING..." : "ASSIGN ACCESS"}</button>
          <p>El usuario debe haber iniciado sesion en PulseOps con el mismo issuer OIDC.</p>
        </form>
      )}

      <div className="memberList">
        {loading && <div className="memberEmpty">Loading identity directory...</div>}
        {!loading && members.length === 0 && <div className="memberEmpty">No team members found.</div>}
        {members.map((member) => (
          <article className="memberRow" key={member.id}>
            <div className="memberIdentity">
              <strong>{member.displayName ?? member.email ?? member.subject}</strong>
              <code>{member.email ?? member.subject}</code>
            </div>
            <span className="youTag">{member.currentUser ? "YOU" : ""}</span>
            {canManage ? (
              <select
                aria-label={`Role for ${member.displayName ?? member.subject}`}
                value={member.role}
                disabled={busyMember === member.id}
                onChange={(event) => void changeRole(member, event.target.value as TeamRole)}
              >
                {ROLES.map((role) => <option key={role}>{role}</option>)}
              </select>
            ) : <span className="memberRole">{member.role}</span>}
            {canManage && <button className="removeMember" disabled={busyMember === member.id} onClick={() => void removeMember(member)}>REMOVE</button>}
          </article>
        ))}
      </div>
    </section>
  );
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
