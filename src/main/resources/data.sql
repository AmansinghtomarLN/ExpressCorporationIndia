-- ===========================================================================
-- Seed data. Uses INSERT ... WHERE NOT EXISTS so it is safe to re-run
-- every local startup. Only the admin user is inserted here.
-- Cities are loaded separately from data/india-cities.csv.
-- Default admin login: admin@expresscorporation.com / Admin@123
-- (BCrypt hash below corresponds to that password - change it in prod!)
-- ===========================================================================

INSERT INTO users (full_name, email, phone, password_hash, role)
SELECT 'System Administrator', 'admin@expresscorporation.com', '9999999999',
       '$2a$10$OndbrhZ.oy6k7bcrpz5gFuRlF8ULj7GeXJARrdKhWjYfT1Y4/dDFu', 'ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@expresscorporation.com');
