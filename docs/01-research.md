# 01 — Research & Positioning

> Purpose: record what already exists, where the gap is, and why BranchLab is scoped the way it is.
> Research date: November 2026. Vendor claims are marked as such — verify before quoting publicly.

---

## 1. The core idea in one line

**Test a business-policy change against what actually happened, before you ship it.**
BranchLab replays a company's real historical events (orders, payments, deliveries, returns) under the
*current* policy and a *proposed* policy, then shows exactly which records would have changed and by how
many rupees.

It does **not** predict how customers would behave differently. It answers: *"Given exactly what happened
last quarter, what would our rules have done differently?"*

---

## 2. What already exists (competitive landscape)

| Category | Products | What they do | Why it is not BranchLab's target |
|---|---|---|---|
| Fraud / card-authorisation rule backtesting | [Lithic](https://docs.lithic.com/docs/backtesting-authorization-rules), [Sift Route Backtesting](https://siftscience.zendesk.com/hc/en-us/articles/13388529878547-Route-Backtesting), [Adyen risk rules](https://help.adyen.com/knowledge/risk/risk-profiles/how-can-i-test-a-rule-before-creating-it) | Run a draft fraud rule over past transactions, compare approvals/declines | Locked to their own payment/fraud platform; fraud rules only |
| Compliance / AML threshold simulation | [Alloy backtesting](https://uk.alloy.com/product-updates/backtesting-for-workflows), [Flagright](https://info.flagright.com/task/blog/aml-platforms-simulate-risk-threshold-changes), [Corsa](https://docs.corsa.finance/transaction-monitoring/testing-rules.md), [Lucinity](https://lucinity.com/blog/lucinitys-time-travel-feature-makes-back-to-the-future-a-reality) | Replay onboarding / transaction-monitoring workflows with changed thresholds | Enterprise fintech compliance, expensive, not commerce policies |
| Credit decisioning | [Taktile](https://www.taktile.com/decision-engine) | Backtest decision flows on historical datasets before deploying | Lending/risk teams at banks; enterprise pricing |
| Usage-based billing / pricing | [Orb Simulations](https://docs.withorb.com/orb-simulations/simulations) | Replay proposed **prices** against past usage → revenue & customer impact | Only pricing, only for companies billing through Orb |
| Billing integration testing | [Stripe test clocks](https://docs.stripe.com/billing/testing/test-clocks) | Move time forward to test renewals/trials in a sandbox | Tests the *integration*, not the *business impact* of a policy |
| In-house builds | [Grab automatic rule backtesting](https://engineering.grab.com/automatic-rule-backtesting) | Spark jobs replay rule scripts over historical data | Proves big companies need it — and build it themselves |
| Code shadow testing | [Twitter Diffy / Opendiffy](https://helpmetest.com/blog/diffy-shadow-mode-testing/), [Microsoft playbook](https://microsoft.github.io/code-with-engineering-playbook/automated-testing/shadow-testing/) | Compare outputs of old vs new **code** on mirrored traffic | Engineering tool for code regressions, not business-policy decisions |
| Rule engines | [GoRules ZEN](https://github.com/gorules/zen) (has an editor simulator), Drools, DecisionRules | Execute rules; test one input at a time | Execute rules, but don't replay history or compute ₹ impact |

### Key takeaways
1. **Demand is proven.** Fintech platforms sell backtesting as a feature, and Grab built it internally. Nobody builds this unless rule changes are risky.
2. **Every existing product is locked to a vertical or a platform.** That means fraud, AML, credit, or one billing vendor.
3. **Nobody serves everyday operational commerce policies.** That means return windows, return fees, COD fees, COD eligibility and free-shipping thresholds, for small and mid-size brands that don't run on an enterprise risk platform.

---

## 3. Chosen beachhead: Indian D2C e-commerce policies

### Why this segment
Indian D2C brands change operational policies often, and each change moves real money:

- **RTO (return-to-origin) is expensive.** A failed COD delivery costs roughly ₹180–450 per order once forward and reverse shipping, handling and blocked inventory are counted. These are vendor-reported estimates ([Base](https://base.com/en-IN/blog/d2c-ecommerce-india-statistics-in-2026/), [ClickPost](https://www.clickpost.ai/blog/ecommerce-rto-statistics-india), [Eshopbox](https://www.eshopbox.com/blog/how-to-reduce-rto-ecommerce-india)).
- **COD RTO rates are high.** COD failure rates are commonly cited at 25–40% for fashion, against roughly 1.5–5% for prepaid orders ([Base COD report](https://base.com/en-IN/blog/cod-return-rates-by-product-category-in-india-2026-report/), [Hillteck](https://www.hillteck.com/blog/rto-trends-2026-indian-d2c.html)).
- **Brands actively experiment with policy levers:**
  - COD "commitment fees" such as a refundable ₹99
  - ₹50–100 return-handling fees
  - COD confirmation steps
  - Disabling COD above a cart value

  ([DFU Publications](https://dfupublications.com/index.php/news/apparel/india-s-online-fashion-growth-resets-with-commitment-fees-margin-discipline), [Razorpay](https://razorpay.com/blog/cash-on-delivery-and-the-challenges/))
- **Today these decisions are made in spreadsheets or by gut feel.** There's no tool that says: "If COD had been disabled above ₹3,000 last quarter, 412 orders would have been blocked. 131 of them actually RTO'd, costing ₹32,750, but ₹6.1L of delivered revenue would have been at risk."

### Why it fits a solo, ₹0 MVP
- **The data is simple and exportable.** Order exports (for example, Shopify CSV) plus shipping and return exports (from aggregators) are just CSV files, so no API partnerships are needed for the MVP.
- **Synthetic data is easy to generate realistically**, with orders, COD/prepaid mix, deliveries, RTOs and returns.
- **The outputs are in rupees**, which anyone, recruiter or founder, understands instantly.

### Later expansion (not in MVP)
- Subscription SaaS policies: refund windows, trial length, grace periods, dunning.
- Marketplace seller policies, insurance claims rules, HR leave policies. The same engine runs with different event schemas and policy templates.

---

## 4. The honesty principle (product differentiator)

Every replay assumes **customers behave exactly as they did historically**. This is the core limitation of
all backtesting, and Lithic explicitly documents a similar one: rules are evaluated in isolation.

BranchLab makes this explicit and separates two layers:

| Layer | What it shows | Label in UI |
|---|---|---|
| **Replay (facts)** | What the rules would have decided on real historical events | "Replay result — based on what actually happened" |
| **Assumptions (estimates)**, post-MVP | Optional, user-entered behaviour assumptions, e.g. "assume 30% of blocked COD orders convert to prepaid" | "Estimate — depends on your assumptions" (visually distinct) |

The MVP ships **facts only**. This is the line that makes the product trustworthy and the engineering
defensible in interviews.

---

## 5. Technical research summary

| Decision | Choice | Evidence |
|---|---|---|
| Rule language | **CEL (Common Expression Language)** via Google's Java implementation `dev.cel` | Non-Turing-complete, designed for safe and fast evaluation, and used for policies in Kubernetes and elsewhere ([google/cel-java](https://github.com/google/cel-java), [Maven](https://central.sonatype.com/artifact/dev.cel/cel)) |
| Rule authoring | Structured **policy templates** (JSON) compiled to CEL; raw CEL only in "advanced" mode | Keeps users and the LLM inside a safe, validated subset |
| AI drafting | **Ollama structured outputs**: pass a JSON Schema in `format`, temperature 0 | Constrains the local model to valid JSON; still validate the meaning server-side ([Ollama docs](https://docs.ollama.com/capabilities/structured-outputs)) |
| Event storage | Append-only event log in PostgreSQL (JSONB payloads, schema version per event) | Event-sourcing guidance: immutable history, versioned payloads, upcasting ([Event Sourcing Versioning](https://leanpub.com/read/esversioning/leanpub-auto-general-versioning-concerns)) |
| Determinism | No wall-clock time, randomness or external lookups inside replay; all inputs are captured in the event | Standard replay guidance ([Gravitee — replay patterns](https://gravitee.io/corpus/gen-1081/event-computing/event-sourcing-patterns-for-auditability-and-replay-in-distributed-systems.html)) |
| Side effects | Replay is a pure computation; it never sends emails or webhooks or writes to source data | Fowler-style "replay mode" guidance (same source) |
| Shadow/diff concepts | Baseline vs candidate comparison, with noise fields excluded | Diffy's model ([helpmetest](https://helpmetest.com/blog/diffy-shadow-mode-testing/)) |

### Pitfalls to design against
- **Logic drift on old data.** Applying new logic to old events that lack a field caused real financial errors in documented cases ([practiceoverflow](https://practiceoverflow.substack.com/p/event-sourcing-anti-patterns-10-ways)). Defence: schema versions, upcasters, and explicit "missing field" handling in templates.
- **Stateful rules** such as "max 2 returns per customer per 90 days" need history. Defence: partition by customer and process events in order within a partition.
- **Rule interactions.** Evaluate the *whole policy set* per side (baseline set vs candidate set), not single rules in isolation.

---

## 6. Name check (before any public launch)
"BranchLab" is a working name. Check domain, trademark (India: IP India) and GitHub/npm name collisions
before using it publicly.
