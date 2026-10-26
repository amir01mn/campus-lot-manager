-- Reference data shared by every environment: university directory and the three campus lots.
-- Demo user accounts are NOT created here (see the demo-profile seeder).

INSERT INTO university_directory (email, user_type) VALUES
    ('aisha.rahman@my.yorku.ca',   'STUDENT'),
    ('daniel.chen@my.yorku.ca',    'STUDENT'),
    ('priya.patel@my.yorku.ca',    'STUDENT'),
    ('lucas.martin@my.yorku.ca',   'STUDENT'),
    ('sofia.rossi@my.yorku.ca',    'STUDENT'),
    ('omar.haddad@my.yorku.ca',    'STUDENT'),
    ('emily.nguyen@my.yorku.ca',   'STUDENT'),
    ('student.demo@my.yorku.ca',   'STUDENT'),
    ('margaret.wong@yorku.ca',     'FACULTY'),
    ('james.okafor@yorku.ca',      'FACULTY'),
    ('elena.petrova@yorku.ca',     'FACULTY'),
    ('robert.singh@yorku.ca',      'FACULTY'),
    ('hannah.kim@yorku.ca',        'FACULTY'),
    ('faculty.demo@yorku.ca',      'FACULTY'),
    ('carlos.mendes@yorku.ca',     'STAFF'),
    ('fatima.ali@yorku.ca',        'STAFF'),
    ('george.brown@yorku.ca',      'STAFF'),
    ('linda.tremblay@yorku.ca',    'STAFF'),
    ('kevin.osei@yorku.ca',        'STAFF'),
    ('staff.demo@yorku.ca',        'STAFF');

INSERT INTO parking_lots (name, location, enabled, created_at) VALUES
    ('Lot A — Keele Campus North', '4700 Keele St, North Entrance, Toronto, ON', true, now()),
    ('Lot B — Stong Pond',         'Stong Pond Rd, Keele Campus, Toronto, ON',   true, now()),
    ('Lot C — Glendon',            '2275 Bayview Ave, Toronto, ON',              true, now());

-- Spot labels are "<prefix>-NNN"; sensor ids are globally unique "SNS-<prefix>-NNN".
INSERT INTO parking_spots (lot_id, label, sensor_id)
SELECT l.id,
       s.prefix || '-' || lpad(n::text, 3, '0'),
       'SNS-' || s.prefix || '-' || lpad(n::text, 3, '0')
FROM (VALUES ('Lot A — Keele Campus North', 'A', 40),
             ('Lot B — Stong Pond',         'B', 60),
             ('Lot C — Glendon',            'C', 30)) AS s(lot_name, prefix, capacity)
JOIN parking_lots l ON l.name = s.lot_name
CROSS JOIN LATERAL generate_series(1, s.capacity) AS n;
