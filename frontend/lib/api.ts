let accessToken: string | null = null;
let restoring: Promise<void> | null = null;

export class ApiError extends Error {
  constructor(readonly status: number, message: string) {
    super(message);
  }
}

async function request(path: string, init: RequestInit = {}) {
  const res = await fetch("/api/v1/" + path, { ...init, credentials: "same-origin", cache: "no-store" });
  if (!res.ok) {
    const problem = await res.json().catch(() => ({}));
    const fields = problem.errors?.map((e: { field: string; message: string }) => `${e.field} ${e.message}`);
    throw new ApiError(res.status, fields?.join(". ") || problem.detail || "The request failed. Please try again.");
  }
  return res;
}

function post(path: string, body: object = {}) {
  return request("auth/" + path, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body),
  });
}

async function refresh() {
  restoring ??= post("refresh").then(async res => {
    accessToken = (await res.json()).accessToken;
  }).finally(() => { restoring = null; });
  await restoring;
}

export async function authenticate(mode: "signup" | "login", fields: Record<string, string>) {
  accessToken = (await (await post(mode, fields)).json()).accessToken;
}

export async function logout() {
  await post("logout");
  accessToken = null;
}

export type Workspace = { name: string; datasets: number; policies: number; recentRuns: string[] };

export async function getWorkspace(): Promise<Workspace> {
  const read = () => request("workspace", { headers: { Authorization: "Bearer " + accessToken } });
  if (!accessToken) await refresh();
  try {
    return await (await read()).json();
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401) throw error;
    await refresh();
    return (await read()).json();
  }
}
