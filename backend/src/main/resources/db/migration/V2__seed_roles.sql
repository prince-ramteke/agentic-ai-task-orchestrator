-- Seed the two roles the system recognizes. Role names ARE the Spring Security authorities
-- (ROLE_USER / ROLE_ADMIN), so hasRole('ADMIN') maps to authority 'ROLE_ADMIN'.
-- Only safe role records are seeded here — NO users and NO passwords are seeded.

INSERT INTO roles (name) VALUES ('ROLE_USER');
INSERT INTO roles (name) VALUES ('ROLE_ADMIN');
