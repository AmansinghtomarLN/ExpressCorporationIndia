-- ===========================================================================
-- Seed data. Uses INSERT ... WHERE NOT EXISTS style guards so it's safe to
-- re-run every startup in dev.
-- Default admin login: admin@shreemahavircourier.com / Admin@123
-- (BCrypt hash below corresponds to that password - change it in prod!)
-- ===========================================================================

INSERT INTO users (full_name, email, phone, password_hash, role)
SELECT 'System Administrator', 'admin@shreemahavircourier.com', '9999999999',
       '$2a$10$OndbrhZ.oy6k7bcrpz5gFuRlF8ULj7GeXJARrdKhWjYfT1Y4/dDFu', 'ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@shreemahavircourier.com');

INSERT INTO branches (branch_name, city, state, pincode, phone, address)
SELECT * FROM (SELECT 'ECI - Mehsana HO' AS branch_name, 'Mehsana' AS city, 'Gujarat' AS state, '384001' AS pincode, '02762-123456' AS phone, 'Near Hanuman Temple, Nagalpur, Mehsana' AS address) t
WHERE NOT EXISTS (SELECT 1 FROM branches WHERE branch_name IN ('ECI - Mehsana HO', 'Mahavir Express - Mehsana HO'));

INSERT INTO branches (branch_name, city, state, pincode, phone, address)
SELECT * FROM (SELECT 'ECI - Ahmedabad', 'Ahmedabad', 'Gujarat', '380001', '079-1234567', 'C.G. Road, Ahmedabad') t
WHERE NOT EXISTS (SELECT 1 FROM branches WHERE branch_name IN ('ECI - Ahmedabad', 'Mahavir Express - Ahmedabad'));

INSERT INTO branches (branch_name, city, state, pincode, phone, address)
SELECT * FROM (SELECT 'ECI - Mumbai', 'Mumbai', 'Maharashtra', '400001', '022-9876543', 'Fort, Mumbai') t
WHERE NOT EXISTS (SELECT 1 FROM branches WHERE branch_name IN ('ECI - Mumbai', 'Mahavir Express - Mumbai'));

INSERT INTO branches (branch_name, city, state, pincode, phone, address)
SELECT * FROM (SELECT 'ECI - Delhi', 'New Delhi', 'Delhi', '110001', '011-4567890', 'Connaught Place, New Delhi') t
WHERE NOT EXISTS (SELECT 1 FROM branches WHERE branch_name IN ('ECI - Delhi', 'Mahavir Express - Delhi'));

INSERT INTO branches (branch_name, city, state, pincode, phone, address)
SELECT * FROM (SELECT 'ECI - Jaipur', 'Jaipur', 'Rajasthan', '302001', '0141-2345678', 'MI Road, Jaipur') t
WHERE NOT EXISTS (SELECT 1 FROM branches WHERE branch_name IN ('ECI - Jaipur', 'Mahavir Express - Jaipur'));

-- Rename legacy branch labels if they still use the old company name
UPDATE branches SET branch_name = 'ECI - Mehsana HO' WHERE branch_name = 'Mahavir Express - Mehsana HO';
UPDATE branches SET branch_name = 'ECI - Ahmedabad' WHERE branch_name = 'Mahavir Express - Ahmedabad';
UPDATE branches SET branch_name = 'ECI - Mumbai' WHERE branch_name = 'Mahavir Express - Mumbai';
UPDATE branches SET branch_name = 'ECI - Delhi' WHERE branch_name = 'Mahavir Express - Delhi';
UPDATE branches SET branch_name = 'ECI - Jaipur' WHERE branch_name = 'Mahavir Express - Jaipur';
-- Demo shipment so a visitor can try the tracking widget immediately
INSERT INTO shipments (tracking_id, sender_name, sender_phone, sender_address, receiver_name, receiver_phone,
                        receiver_address, origin_city, destination_city, weight_kg, service_type, status, expected_delivery)
SELECT * FROM (SELECT
    'MH1000000001' AS tracking_id, 'Rakesh Shah' AS sender_name, '9825012345' AS sender_phone,
    '12 Textile Market, Mehsana, Gujarat' AS sender_address, 'Priya Nair' AS receiver_name, '9880012345' AS receiver_phone,
    '45 MG Road, Bengaluru, Karnataka' AS receiver_address, 'Mehsana' AS origin_city, 'Bengaluru' AS destination_city,
    1.20 AS weight_kg, 'DOMESTIC_EXPRESS' AS service_type, 'IN_TRANSIT' AS status, DATE_ADD(CURDATE(), INTERVAL 2 DAY) AS expected_delivery
) t
WHERE NOT EXISTS (SELECT 1 FROM shipments WHERE tracking_id = 'MH1000000001');

INSERT INTO tracking_events (shipment_id, status, location, remarks, event_time)
SELECT s.id, 'BOOKED', 'Mehsana Hub', 'Shipment booked and label generated', DATE_SUB(NOW(), INTERVAL 3 DAY)
FROM shipments s WHERE s.tracking_id = 'MH1000000001'
  AND NOT EXISTS (SELECT 1 FROM tracking_events e WHERE e.shipment_id = s.id AND e.status = 'BOOKED');

INSERT INTO tracking_events (shipment_id, status, location, remarks, event_time)
SELECT s.id, 'PICKED_UP', 'Mehsana Hub', 'Picked up from sender', DATE_SUB(NOW(), INTERVAL 2 DAY)
FROM shipments s WHERE s.tracking_id = 'MH1000000001'
  AND NOT EXISTS (SELECT 1 FROM tracking_events e WHERE e.shipment_id = s.id AND e.status = 'PICKED_UP');

INSERT INTO tracking_events (shipment_id, status, location, remarks, event_time)
SELECT s.id, 'IN_TRANSIT', 'Ahmedabad Sorting Hub', 'Departed from sorting hub towards destination', DATE_SUB(NOW(), INTERVAL 1 DAY)
FROM shipments s WHERE s.tracking_id = 'MH1000000001'
  AND NOT EXISTS (SELECT 1 FROM tracking_events e WHERE e.shipment_id = s.id AND e.status = 'IN_TRANSIT');

-- Default rate cards (freight pricing)
INSERT INTO rate_cards (service_type, min_weight_kg, max_weight_kg, base_rate, per_kg_rate, active)
SELECT 'DOMESTIC_STANDARD', 0, 999, 80.00, 15.00, 1
WHERE NOT EXISTS (SELECT 1 FROM rate_cards WHERE service_type = 'DOMESTIC_STANDARD');

INSERT INTO rate_cards (service_type, min_weight_kg, max_weight_kg, base_rate, per_kg_rate, active)
SELECT 'DOMESTIC_EXPRESS', 0, 999, 120.00, 25.00, 1
WHERE NOT EXISTS (SELECT 1 FROM rate_cards WHERE service_type = 'DOMESTIC_EXPRESS');

INSERT INTO rate_cards (service_type, min_weight_kg, max_weight_kg, base_rate, per_kg_rate, active)
SELECT 'INTERNATIONAL', 0, 999, 450.00, 80.00, 1
WHERE NOT EXISTS (SELECT 1 FROM rate_cards WHERE service_type = 'INTERNATIONAL');
