"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import Brand from "./Brand";
import { authenticate } from "../lib/api";

export default function AuthForm({ signup = false }: { signup?: boolean }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const fields: Record<string, string> = { email: String(data.get("email")).trim(), password: String(data.get("password")) };
    if (signup) fields.workspaceName = String(data.get("workspaceName")).trim();
    setBusy(true);
    setError("");
    try {
      await authenticate(signup ? "signup" : "login", fields);
      router.replace("/workspace");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Please try again.");
      setBusy(false);
    }
  }

  return (
    <main className="auth-shell">
      <section className="access">
        <Brand />
        <div className="form-wrap">
          <h1>{signup ? "Create your workspace" : "Welcome back"}</h1>
          <p className="subtle">{signup ? "Set up a workspace for your brand." : "Sign in to your BranchLab workspace."}</p>
          <form onSubmit={submit}>
            {signup && <label>Workspace name<input name="workspaceName" autoComplete="organization" maxLength={120} required /></label>}
            <label>Email<input type="email" name="email" autoComplete="email" placeholder="you@company.com" maxLength={254} required /></label>
            <div>
              <label htmlFor="password">Password<input id="password" type="password" name="password"
                autoComplete={signup ? "new-password" : "current-password"} minLength={signup ? 8 : undefined}
                maxLength={72} aria-describedby={signup ? "password-hint" : undefined} required /></label>
              {signup && <small id="password-hint">Use at least 8 characters.</small>}
            </div>
            {error && <p role="alert" className="error">{error}</p>}
            <button className="primary" disabled={busy}>{busy ? "Please wait…" : signup ? "Create workspace" : "Sign in"}</button>
          </form>
          <p className="switch">{signup ? "Already have an account? " : "New to BranchLab? "}
            <Link href={signup ? "/login" : "/signup"}>{signup ? "Sign in" : "Create an account"}</Link>
          </p>
        </div>
      </section>
      <aside className="policy-preview">
        <div className="preview-inner">
          <h2>What if your COD<br />policy changed?</h2>
          <p>Compare two policies on the same past orders. See exactly which orders change and by how much.</p>
          <figure className="comparison">
            <figcaption>Example order <strong>₹999</strong></figcaption>
            <div className="branch-lines" aria-hidden="true" />
            <div className="arms">
              <div><span className="arm-label"><i />Baseline</span><strong>₹0</strong><small>COD fee</small></div>
              <div><span className="arm-label"><i />Candidate</span><strong>₹49</strong><small>COD fee</small></div>
            </div>
          </figure>
        </div>
      </aside>
    </main>
  );
}
