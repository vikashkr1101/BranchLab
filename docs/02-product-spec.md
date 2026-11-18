# 02 — Product Specification (PRD)

> Status: v1 scope for MVP. Read 01-research.md first for the "why".

---

## 1. Problem statement

D2C brands regularly change operational policies, such as:
- return window
- return fee
- COD fee
- COD eligibility
- free-shipping threshold

Each change moves margin. Today, teams decide using spreadsheets or intuition. The real impact only shows up weeks after the change is live, when a bad decision has already cost money.

**BranchLab lets a brand test a proposed policy against its own real order history and see the exact,
order-level difference in rupees before changing anything in production.**

## 2. Goals and non-goals

### Goals (MVP)
1. Import or generate a historical event dataset (orders → payment → shipment → delivery/RTO → return → refund).
2. Define policies with safe, structured templates, either through forms or by drafting from plain English with AI.
3. Replay any date range under **Baseline** (current policy set) and **Candidate** (proposed policy set).
4. Show the difference:
   - summary in ₹
   - counts
   - every affected order, with a clause-level explanation of why it changed
5. Guarantee determinism: the same dataset, policies and engine version always produce the same result, which is verifiable by a result hash.
6. Run entirely at ₹0: local Docker, open-source components, and a local LLM that's optional.

### Non-goals (MVP)
- Predicting customer behaviour change (the assumption layer comes later, clearly labelled as an estimate).
- Live integrations with Shopify, Razorpay or Shiprocket APIs (CSV import only).
- Pushing policies back into production systems.
- Multi-currency, taxes and GST computation.
- Real-time streaming replay; batch replay only.
- Enterprise features (SSO, RBAC beyond owner/viewer, audit exports).

## 3. Personas

| Persona | Who | Job to be done | What they need to see |
|---|---|---|---|
| **Founder / Head of Ops** (primary) | Runs a D2C brand doing 2k–50k orders/month | "Should we add a ₹49 COD fee?" | ₹ impact, orders affected, a trade-off in one screen |
| **Growth / Finance analyst** | Owns margin and reporting | Compare 3–4 variants and defend the choice to the founder | Side-by-side variants, exportable results, an exact breakdown |
| **Engineer** (secondary) | Implements the policy in code later | Know the exact rule and its edge cases | The rule definition (CEL), test cases, per-order traces |
| **Portfolio viewer / recruiter** (demo persona) | Visits the live demo | Understand the product in 60 seconds | A seeded demo workspace, a one-click sample experiment, a guided tour |

## 4. Core concepts (glossary)

| Term | Meaning |
|---|---|
| **Workspace** | One brand/company. Owns datasets, policies and experiments. |
| **Dataset** | An immutable set of historical events (imported CSV or generated). Has a checksum. |
| **Event** | One fact that happened, such as `ORDER_PLACED` or `DELIVERED`. Immutable. |
| **Policy** | A named business rule of one **policy type**, e.g. "Return policy". |
| **Policy version** | An immutable snapshot of a policy's parameters plus its compiled CEL. Policies are never edited in place; editing creates a new version. |
| **Policy set** | The collection of policy versions applied together (one per type). Baseline and Candidate are each a policy set. |
| **Experiment** | A question: dataset + date range + filters + Baseline set + Candidate set (or up to 3 candidates). |
| **Run** | One execution of an experiment. Produces per-order outcomes, diffs, a summary and a result hash. |
| **Outcome** | What a policy set decided for one order: fees charged, eligibility and refund amounts, with a trace. |
| **Trace** | The list of policy clauses evaluated for an order, with their inputs and results (explainability). |

## 5. Policy types in MVP (D2C)

All money is in INR paise internally (integers) and shown as ₹.

### P1. Return policy
| Parameter | Type | Example |
|---|---|---|
| `windowDays` | int 0–60 | 7 |
| `windowStartsFrom` | enum `DELIVERY` / `ORDER` | DELIVERY |
| `excludedCategories` | list<string> | ["innerwear", "sale"] |
| `returnFee` | money or % of item | ₹49 |
| `freeReturnAboveOrderValue` | money, optional | ₹1,999 |
| `maxReturnsPerCustomerPer90Days` | int, optional | 2 (stateful rule) |

**Decides per return request:**
- eligible / ineligible, with a reason
- the fee charged
- the refund amount

### P2. COD policy
| Parameter | Type | Example |
|---|---|---|
| `codEnabled` | bool | true |
| `maxOrderValueForCod` | money, optional | ₹3,000 |
| `codFee` | flat money and/or % | ₹49 |
| `codFeeWaivedAbove` | money, optional | ₹1,499 |
| `blockedPincodeTiers` | list<enum TIER1/TIER2/TIER3> | [] |

**Decides per order:**
- COD allowed / blocked
- the COD fee charged
- **attributed RTO cost avoided**, if blocked and the order actually RTO'd
- **revenue at risk**, if blocked and the order was actually delivered

### P3. Shipping policy
| Parameter | Type | Example |
|---|---|---|
| `shippingFee` | money | ₹79 |
| `freeShippingAbove` | money | ₹799 |
| `codExtraShipping` | money, optional | ₹0 |

**Decides per order:** the shipping fee charged.

> **Post-MVP types:** first-order discount and stacking rules, exchange-only windows, prepaid incentives, subscription refund/grace policies.

## 6. User stories with acceptance criteria

### Datasets
- **US-1** As a founder, I can load a **demo dataset** in one click.
  **AC:**
  - 10,000+ orders over 6 months are generated with a fixed seed.
  - The dataset is identical on every machine for the same seed.
  - It is ready in under 10 s.
- **US-2** As an analyst, I can **import CSVs** (orders, shipments, returns) and map columns.
  **AC:**
  - Show a column-mapping UI with auto-detection for Shopify-style order export headers.
  - Validate rows and show row-level errors.
  - Accept the import only if it has 0 blocking errors.
  - Create an immutable dataset with a checksum.
- **US-3** I can see dataset health.
  **AC:**
  - Show counts per event type, date range and COD/prepaid mix.
  - Show RTO and return rates.
  - List missing-field warnings.

### Policies
- **US-4** I can create a policy from a **form** for a policy type.
  **AC:**
  - Parameters are validated by schema.
  - The compiled CEL is shown read-only.
  - Saving creates version v1.
- **US-5** I can **describe a policy in English** and get a draft.
  **AC:**
  - The AI returns a template-JSON draft that is validated against the schema.
  - The UI shows a diff of the draft against the current version.
  - Nothing is saved without explicit user approval.
  - If the AI is unavailable, the form still works.
- **US-6** I can **test a policy on a sample order** before running a full replay.
  **AC:** pick or edit one order and see the outcome and trace instantly (under 200 ms).
- **US-7** Policy edits create **new versions**; old versions are never mutated.
  **AC:** version history with a parameter diff between any two versions.

### Experiments & runs
- **US-8** I can create an experiment: dataset, date range, optional filters (category, payment mode, pincode tier), Baseline set and 1–3 Candidate sets.
- **US-9** I can **run** it and watch progress.
  **AC:**
  - Show live progress (events processed / total) via SSE.
  - The run can be cancelled.
  - Runs execute asynchronously; the UI is never blocked.
- **US-10** I see a **results summary**.
  **AC:**
  - Cards: net revenue Δ, fees Δ, refunds Δ, orders affected, RTO cost avoided, revenue at risk.
  - Every number has a tooltip formula.
- **US-11** I can **drill into affected orders**.
  **AC:**
  - A paginated, filterable table of changed orders.
  - Clicking a row shows Baseline vs Candidate outcomes side by side, plus both traces.
- **US-12** Results are **reproducible**.
  **AC:**
  - Every run stores a `resultHash`.
  - "Re-run & verify" produces the same hash; on a mismatch, the UI flags it loudly.
- **US-13** I can **export** results as CSV (summary + affected orders).
- **US-14** I can compare **multiple candidates** side by side (up to 3) on one summary table.

### Demo & trust
- **US-15** A public **demo mode** opens a seeded read-only workspace with a 4-step guided tour.
- **US-16** Every results screen shows the honesty banner: "Replay of what actually happened. Does not predict behaviour change."

## 7. Key metrics (what the product computes)

All computed per order per side (Baseline / Candidate), then aggregated:

| Metric | Formula (per order) |
|---|---|
| `shippingCharged` | from the shipping policy |
| `codFeeCharged` | from the COD policy (0 if prepaid or blocked) |
| `codBlocked` | bool from the COD policy |
| `returnEligible` | bool per return request |
| `returnFeeCharged` | from the return policy, if eligible |
| `refundAmount` | item value − return fee, if eligible; 0 if ineligible |
| `netCollected` | items + shipping + COD fee − refund (for orders not blocked) |
| `rtoCostAvoided` | if `codBlocked` and the actual outcome was RTO → configured RTO cost (default ₹250, editable per workspace) |
| `revenueAtRisk` | if `codBlocked` and the actual outcome was DELIVERED → order item value |

**Deltas** = Candidate − Baseline. An "affected order" is any order where at least one metric differs.

## 8. Success metrics

### Product (if it becomes a startup)
- **Activation:** % of new workspaces that complete their first run within 15 minutes.
- **Value:** a policy change was shipped after an experiment (self-reported).
- **Retention:** workspaces that run 3+ experiments in 30 days.

### Portfolio (job switch)
- A live demo where a visitor reaches a results screen in under 60 s.
- An architecture write-up, plus a determinism proof (the same hash across reruns).
- 100k events replayed in under 10 s locally, with a documented benchmark.

## 9. Risks & mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Users read replay as a prediction | Wrong decisions, loss of trust | Honesty banner; facts-only MVP; assumptions later, clearly labelled |
| Messy real CSVs | Import fails | Column mapping, row-level validation, a downloadable error report |
| The LLM drafts a wrong rule | Silent wrong results | Schema-constrained output, server validation, a human approval diff, sample-order test |
| Stateful rules are slow | Long runs | Partition by customer, ordered within a partition, parallel across partitions |
| Old events miss new fields | Wrong numbers | Event schema versions plus upcasters; templates declare required fields; a missing field marks the order "unevaluable" (counted, never silently zero) |
| Scope creep (more policy types) | MVP never ships | Freeze to 3 policy types until M6 is done |
| Name conflict | Rebrand later | Check before going public |

## 10. Pricing hypothesis (later, if it becomes a startup)
- **Free:** 1 workspace, 50k events, demo data.
- **Growth:** about ₹2,999/month, 1M events, CSV import, 5 users.
- **Pro:** live integrations (Shopify, Shiprocket), scheduled re-runs, the assumptions layer.

Validate with 10 D2C founder interviews before building any paid features.
