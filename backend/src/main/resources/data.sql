-- Seed data (adapted from brief Addendum A) — idempotent via ON CONFLICT
-- so restarting the app doesn't duplicate rows.

INSERT INTO agents (id, name, active_order_count, status, current_zone) VALUES
  ('AGT-001', 'Priya Sharma',  2, 'BUSY',      NULL),
  ('AGT-002', 'Rahul Verma',   0, 'AVAILABLE', NULL),
  ('AGT-003', 'Ananya Iyer',   1, 'BUSY',      NULL),
  ('AGT-004', 'Kiran Nair',    0, 'AVAILABLE', NULL),
  ('AGT-005', 'Deepak Mehta',  3, 'BUSY',      NULL)
ON CONFLICT (id) DO NOTHING;

INSERT INTO orders (id, description, assigned_agent_id, status, created_at) VALUES
  ('ORD-001', 'Electronics — Koramangala to Indiranagar', 'AGT-001', 'ASSIGNED', NOW()),
  ('ORD-002', 'Groceries — HSR Layout to BTM',            'AGT-001', 'ASSIGNED', NOW()),
  ('ORD-003', 'Pharma — Whitefield to Marathahalli',      'AGT-003', 'ASSIGNED', NOW()),
  ('ORD-004', 'Documents — MG Road to Jayanagar',         'AGT-005', 'ASSIGNED', NOW()),
  ('ORD-005', 'Food — Bellandur to Electronic City',      'AGT-005', 'ASSIGNED', NOW()),
  ('ORD-006', 'Apparel — Malleshwaram to Rajajinagar',    'AGT-005', 'ASSIGNED', NOW()),
  ('ORD-007', 'Books — Banashankari to JP Nagar',         'AGT-003', 'ASSIGNED', NOW()),
  ('ORD-008', 'Hardware — Peenya to Yeshwanthpur',        'AGT-001', 'ASSIGNED', NOW())
ON CONFLICT (id) DO NOTHING;
