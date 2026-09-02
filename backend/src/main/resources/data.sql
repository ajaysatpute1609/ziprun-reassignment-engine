-- Seed data (adapted from brief Addendum A) — idempotent via ON CONFLICT
-- so restarting the app doesn't duplicate rows.

INSERT INTO agents (id, name, active_order_count, status, current_zone, max_capacity) VALUES
  ('AGT-001', 'Priya Sharma',  2, 'BUSY',      'Koramangala',  4),
  ('AGT-002', 'Rahul Verma',   0, 'AVAILABLE', 'Indiranagar',  4),
  ('AGT-003', 'Ananya Iyer',   1, 'BUSY',      'Whitefield',   3),
  ('AGT-004', 'Kiran Nair',    0, 'AVAILABLE', 'JP Nagar',     3),
  ('AGT-005', 'Deepak Mehta',  3, 'BUSY',      'Jayanagar',    3)
ON CONFLICT (id) DO NOTHING;

-- SLA deadlines staggered for demo purposes: some already breached (red),
-- some imminent (amber), some comfortable (green) — see dispatch board.
INSERT INTO orders (id, description, assigned_agent_id, status, created_at, sla_deadline) VALUES
  ('ORD-001', 'Electronics — Koramangala to Indiranagar', 'AGT-001', 'ASSIGNED', NOW(), NOW() + INTERVAL '55 minutes'),
  ('ORD-002', 'Groceries — HSR Layout to BTM',            'AGT-001', 'ASSIGNED', NOW(), NOW() + INTERVAL '40 minutes'),
  ('ORD-003', 'Pharma — Whitefield to Marathahalli',      'AGT-003', 'ASSIGNED', NOW(), NOW() + INTERVAL '8 minutes'),
  ('ORD-004', 'Documents — MG Road to Jayanagar',         'AGT-005', 'ASSIGNED', NOW(), NOW() + INTERVAL '3 minutes'),
  ('ORD-005', 'Food — Bellandur to Electronic City',      'AGT-005', 'ASSIGNED', NOW(), NOW() - INTERVAL '5 minutes'),
  ('ORD-006', 'Apparel — Malleshwaram to Rajajinagar',    'AGT-005', 'ASSIGNED', NOW(), NOW() + INTERVAL '30 minutes'),
  ('ORD-007', 'Books — Banashankari to JP Nagar',         'AGT-003', 'ASSIGNED', NOW(), NOW() + INTERVAL '20 minutes'),
  ('ORD-008', 'Hardware — Peenya to Yeshwanthpur',        'AGT-001', 'ASSIGNED', NOW(), NOW() + INTERVAL '60 minutes')
ON CONFLICT (id) DO NOTHING;
