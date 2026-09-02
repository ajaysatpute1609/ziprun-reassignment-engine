-- Seed data — idempotent via ON CONFLICT so restarts don't duplicate rows.

INSERT INTO agents (id, name, active_order_count, status, current_zone, max_capacity, can_handle_heavy) VALUES
  ('AGT-001', 'Priya Sharma',   3, 'BUSY',      'Koramangala',    4, true),
  ('AGT-002', 'Rahul Verma',    1, 'AVAILABLE', 'Indiranagar',    4, true),
  ('AGT-003', 'Ananya Iyer',    2, 'BUSY',      'Whitefield',     3, true),
  ('AGT-004', 'Kiran Nair',     1, 'AVAILABLE', 'JP Nagar',       3, false),
  ('AGT-005', 'Deepak Mehta',   3, 'BUSY',      'Jayanagar',      3, true),
  ('AGT-006', 'Ravi Kumar',     1, 'AVAILABLE', 'MG Road',        4, true),
  ('AGT-007', 'Sneha Rao',      1, 'AVAILABLE', 'Electronic City',3, true),
  ('AGT-008', 'Vikram Singh',   1, 'AVAILABLE', 'Rajajinagar',    4, true),
  ('AGT-009', 'Pooja Nair',     1, 'AVAILABLE', 'BTM',            3, true),
  ('AGT-010', 'Arjun Reddy',    1, 'AVAILABLE', 'HSR Layout',     4, false),
  ('AGT-011', 'Meera Desai',    0, 'AVAILABLE', 'Marathahalli',   3, true),
  ('AGT-012', 'Karthik Iyer',   0, 'AVAILABLE', 'Bellandur',      4, true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO orders (id, description, assigned_agent_id, status, created_at, sla_deadline, pickup_zone, dropoff_zone, weight_class) VALUES
  ('ORD-001', 'Electronics — Koramangala to Indiranagar', 'AGT-001', 'ASSIGNED', NOW(), NOW() + INTERVAL '55 minutes', 'Koramangala',   'Indiranagar',   'LIGHT'),
  ('ORD-002', 'Groceries — HSR Layout to BTM',            'AGT-001', 'ASSIGNED', NOW(), NOW() + INTERVAL '40 minutes', 'HSR Layout',    'BTM',           'LIGHT'),
  ('ORD-003', 'Pharma — Whitefield to Marathahalli',      'AGT-003', 'ASSIGNED', NOW(), NOW() + INTERVAL '8 minutes',  'Whitefield',    'Marathahalli',  'LIGHT'),
  ('ORD-004', 'Furniture — MG Road to Jayanagar',         'AGT-005', 'ASSIGNED', NOW(), NOW() + INTERVAL '3 minutes',  'MG Road',       'Jayanagar',     'HEAVY'),
  ('ORD-005', 'Food — Bellandur to Electronic City',      'AGT-005', 'ASSIGNED', NOW(), NOW() - INTERVAL '5 minutes',  'Bellandur',     'Electronic City','LIGHT'),
  ('ORD-006', 'Apparel — Malleshwaram to Rajajinagar',    'AGT-005', 'ASSIGNED', NOW(), NOW() + INTERVAL '30 minutes', 'Malleshwaram',  'Rajajinagar',   'LIGHT'),
  ('ORD-007', 'Books — Banashankari to JP Nagar',         'AGT-003', 'ASSIGNED', NOW(), NOW() + INTERVAL '20 minutes', 'Banashankari',  'JP Nagar',      'LIGHT'),
  ('ORD-008', 'Hardware — Peenya to Yeshwanthpur',        'AGT-001', 'ASSIGNED', NOW(), NOW() + INTERVAL '60 minutes', 'Peenya',        'Yeshwanthpur',  'HEAVY'),
  ('ORD-009', 'Cake — Indiranagar to BTM',                'AGT-002', 'ASSIGNED', NOW(), NOW() + INTERVAL '12 minutes', 'Indiranagar',   'BTM',           'LIGHT'),
  ('ORD-010', 'Documents — JP Nagar to Electronic City',  'AGT-004', 'ASSIGNED', NOW(), NOW() + INTERVAL '25 minutes', 'JP Nagar',      'Electronic City','LIGHT'),
  ('ORD-011', 'TV — MG Road to Rajajinagar',              'AGT-006', 'ASSIGNED', NOW(), NOW() + INTERVAL '35 minutes', 'MG Road',       'Rajajinagar',   'HEAVY'),
  ('ORD-012', 'Flowers — Electronic City to Whitefield',  'AGT-007', 'ASSIGNED', NOW(), NOW() + INTERVAL '15 minutes', 'Electronic City','Whitefield',     'LIGHT'),
  ('ORD-013', 'Toys — Rajajinagar to Koramangala',        'AGT-008', 'ASSIGNED', NOW(), NOW() + INTERVAL '45 minutes', 'Rajajinagar',   'Koramangala',   'LIGHT'),
  ('ORD-014', 'Heavy package — BTM to Jayanagar',         'AGT-009', 'ASSIGNED', NOW(), NOW() + INTERVAL '5 minutes',  'BTM',           'Jayanagar',     'HEAVY'),
  ('ORD-015', 'Courier — HSR Layout to Indiranagar',      'AGT-010', 'ASSIGNED', NOW(), NOW() + INTERVAL '50 minutes', 'HSR Layout',    'Indiranagar',   'LIGHT')
ON CONFLICT (id) DO NOTHING;
