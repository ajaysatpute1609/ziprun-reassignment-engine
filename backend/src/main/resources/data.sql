-- Seed data (adapted from brief Addendum A) — idempotent via ON CONFLICT
-- so restarting the app doesn't duplicate rows.

-- Sprint 2: zone + capacity + heavy-carry capability per agent.
-- AGT-004 is deliberately marked unable to carry HEAVY orders (e.g. a
-- two-wheeler rider) to exercise the weight-class eligibility rule.
INSERT INTO agents (id, name, active_order_count, status, current_zone, max_capacity, can_handle_heavy) VALUES
  ('AGT-001', 'Priya Sharma',  2, 'BUSY',      'Koramangala',  4, true),
  ('AGT-002', 'Rahul Verma',   0, 'AVAILABLE', 'Indiranagar',  4, true),
  ('AGT-003', 'Ananya Iyer',   1, 'BUSY',      'Whitefield',   3, true),
  ('AGT-004', 'Kiran Nair',    0, 'AVAILABLE', 'JP Nagar',     3, false),
  ('AGT-005', 'Deepak Mehta',  3, 'BUSY',      'Jayanagar',    3, true)
ON CONFLICT (id) DO NOTHING;

-- SLA deadlines staggered for demo purposes: some already breached (red),
-- some imminent (amber), some comfortable (green) — see dispatch board.
-- Sprint 2: pickup/dropoff zones + weight class per order. ORD-004 is HEAVY
-- and currently sits with AGT-005 (can_handle_heavy=true) — if it ever needs
-- reassignment, AGT-004 must never be recommended for it.
INSERT INTO orders (id, description, assigned_agent_id, status, created_at, sla_deadline, pickup_zone, dropoff_zone, weight_class) VALUES
  ('ORD-001', 'Electronics — Koramangala to Indiranagar', 'AGT-001', 'ASSIGNED', NOW(), NOW() + INTERVAL '55 minutes', 'Koramangala', 'Indiranagar', 'LIGHT'),
  ('ORD-002', 'Groceries — HSR Layout to BTM',            'AGT-001', 'ASSIGNED', NOW(), NOW() + INTERVAL '40 minutes', 'HSR Layout',  'BTM',         'LIGHT'),
  ('ORD-003', 'Pharma — Whitefield to Marathahalli',      'AGT-003', 'ASSIGNED', NOW(), NOW() + INTERVAL '8 minutes',  'Whitefield',  'Marathahalli','LIGHT'),
  ('ORD-004', 'Furniture — MG Road to Jayanagar',         'AGT-005', 'ASSIGNED', NOW(), NOW() + INTERVAL '3 minutes',  'MG Road',     'Jayanagar',   'HEAVY'),
  ('ORD-005', 'Food — Bellandur to Electronic City',      'AGT-005', 'ASSIGNED', NOW(), NOW() - INTERVAL '5 minutes',  'Bellandur',   'Electronic City','LIGHT'),
  ('ORD-006', 'Apparel — Malleshwaram to Rajajinagar',    'AGT-005', 'ASSIGNED', NOW(), NOW() + INTERVAL '30 minutes', 'Malleshwaram','Rajajinagar', 'LIGHT'),
  ('ORD-007', 'Books — Banashankari to JP Nagar',         'AGT-003', 'ASSIGNED', NOW(), NOW() + INTERVAL '20 minutes', 'Banashankari','JP Nagar',    'LIGHT'),
  ('ORD-008', 'Hardware — Peenya to Yeshwanthpur',        'AGT-001', 'ASSIGNED', NOW(), NOW() + INTERVAL '60 minutes', 'Peenya',      'Yeshwanthpur','HEAVY')
ON CONFLICT (id) DO NOTHING;
