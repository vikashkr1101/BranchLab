"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Brand from "../../components/Brand";
import { ApiError, getWorkspace, logout, type Workspace } from "../../lib/api";

export default function WorkspacePage() {
  const router = useRouter();
  const [workspace, setWorkspace] = useState<Workspace | null>(null);
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    getWorkspace().then(data => { if (active) setWorkspace(data); }).catch(err => {
      if (!active) return;
      if (err instanceof ApiError && err.status === 401) router.replace("/login");
      else setError(err instanceof Error ? err.message : "Could not load your workspace.");
    });
    return () => { active = false; };
  }, [router, attempt]);

  async function signOut() {
    try {
      await logout();
      router.replace("/login");
    } catch {
      setError("Sign out failed. Please try again.");
    }
  }

  return <div className="workspace">
    <header><Brand />{workspace && <button className="secondary" onClick={signOut}>Sign out</button>}</header>
    <main className="home">
      {error && <div role="alert" className="error">{error} <button onClick={() => { setError(""); setAttempt(n => n + 1); }}>Retry</button></div>}
      {workspace ? <>
        <p className="eyebrow">Workspace</p>
        <h1>{workspace.name}</h1>
        <div className="cards">
          <section><h2>Datasets</h2><strong>{workspace.datasets}</strong><p>Historical order datasets</p></section>
          <section><h2>Policies</h2><strong>{workspace.policies}</strong><p>Policies in this workspace</p></section>
        </div>
        <section className="runs"><h2>Recent runs</h2>{workspace.recentRuns.length
          ? <ul>{workspace.recentRuns.map((name, i) => <li key={i}>{name}</li>)}</ul>
          : <div className="empty"><h3>No runs yet</h3><p>Your recent experiment runs will appear here.</p></div>}</section>
      </> : !error && <div role="status" aria-label="Loading workspace" className="skeleton" />}
    </main>
  </div>;
}
