create table workspace (
  id uuid primary key, name text not null,
  rto_cost_paise bigint not null default 25000,      -- ₹250 default, editable
  created_at timestamptz not null default now()
);

create table app_user (
  id uuid primary key, workspace_id uuid not null references workspace(id),
  email text not null unique, password_hash text not null,
  role text not null check (role in ('OWNER','VIEWER')),
  created_at timestamptz not null default now()
);

create table dataset (
  id uuid primary key, workspace_id uuid not null references workspace(id),
  name text not null, source text not null check (source in ('GENERATED','CSV')),
  seed bigint,                                  -- for GENERATED
  status text not null check (status in ('IMPORTING','READY','FAILED')),
  event_count int not null default 0,
  checksum text,                                -- sha256 over ordered events
  period_from date, period_to date,
  created_at timestamptz not null default now()
);

create table event (
  dataset_id uuid not null references dataset(id),
  seq bigint not null,                          -- global order within dataset
  event_id uuid not null,
  type text not null,                           -- ORDER_PLACED, DELIVERED, ...
  schema_version int not null default 1,
  order_id text not null,
  customer_id text not null,                    -- hashed in imports (no raw PII)
  occurred_at timestamptz not null,
  payload jsonb not null,
  primary key (dataset_id, seq)
);
create index event_customer_idx on event (dataset_id, customer_id, seq);
create index event_time_idx on event (dataset_id, occurred_at);

create table policy (
  id uuid primary key, workspace_id uuid not null references workspace(id),
  type text not null check (type in ('RETURN','COD','SHIPPING')),
  name text not null, created_at timestamptz not null default now()
);

create table policy_version (
  id uuid primary key, policy_id uuid not null references policy(id),
  version int not null,
  params jsonb not null,                        -- validated template parameters
  cel_source text not null,                     -- compiled, shown read-only
  params_hash text not null,                    -- sha256(canonical params json)
  created_by uuid references app_user(id),
  created_via text not null check (created_via in ('FORM','AI_DRAFT','ADVANCED')),
  created_at timestamptz not null default now(),
  unique (policy_id, version)
);

create table experiment (
  id uuid primary key, workspace_id uuid not null references workspace(id),
  name text not null, dataset_id uuid not null references dataset(id),
  period_from date not null, period_to date not null,
  filters jsonb not null default '{}',          -- {categories, paymentModes, pincodeTiers}
  created_at timestamptz not null default now()
);

create table experiment_arm (                   -- BASELINE + 1..3 CANDIDATE arms
  id uuid primary key, experiment_id uuid not null references experiment(id),
  label text not null, kind text not null check (kind in ('BASELINE','CANDIDATE')),
  policy_version_ids uuid[] not null            -- one version per policy type
);

create table run (
  id uuid primary key, experiment_id uuid not null references experiment(id),
  status text not null check (status in ('QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELLED')),
  engine_version text not null,
  input_hash text not null,                     -- dataset checksum + arm params hashes + engine version + filters
  result_hash text,                             -- sha256 over ordered outcomes
  events_total int, events_processed int default 0,
  error text, started_at timestamptz, finished_at timestamptz,
  created_at timestamptz not null default now()
);

create table run_outcome (                      -- one row per order per arm
  run_id uuid not null references run(id),
  arm_id uuid not null, order_id text not null,
  metrics jsonb not null,                       -- shippingCharged, codFeeCharged, ...
  trace jsonb not null,                         -- clause-level explanation
  evaluable boolean not null default true,
  primary key (run_id, arm_id, order_id)
);

create table run_diff (                         -- only orders where a candidate differs from baseline
  run_id uuid not null references run(id),
  candidate_arm_id uuid not null, order_id text not null,
  changed_metrics text[] not null,
  delta jsonb not null,                         -- metric -> paise delta
  primary key (run_id, candidate_arm_id, order_id)
);

create table run_summary (
  run_id uuid not null references run(id),
  arm_id uuid not null,
  totals jsonb not null,                        -- aggregated metrics
  affected_orders int not null default 0,
  unevaluable_orders int not null default 0,
  primary key (run_id, arm_id)
);

create table job (
  id uuid primary key, type text not null, ref_id uuid not null,
  status text not null check (status in ('QUEUED','RUNNING','DONE','FAILED','CANCELLED')),
  attempts int not null default 0, locked_at timestamptz, cancel_requested boolean default false,
  created_at timestamptz not null default now()
);
create index job_queue_idx on job (status, created_at);
