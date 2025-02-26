DELETE FROM "user" WHERE "id"=1;
INSERT INTO "user" ("id", "email", "name", "password", "phone", "username", "is_enabled") VALUES (1, NULL, '管理员', '$2a$10$nVYak7TjO3td5Py/cYcDWOiAQVS8r6f/uN.ErMBn/P5oM/ld61Z9G', NULL, 'root', true);

DELETE FROM "authority" WHERE "id"=1;
INSERT INTO "authority" ("id","authority", "user_id") VALUES (1, 'ADMIN', '1');
DELETE FROM "authority" WHERE "id"=2;
INSERT INTO "authority" ("id","authority", "user_id")  VALUES (2, 'USER', '1');


