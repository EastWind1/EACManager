DELETE FROM "user" WHERE "id"=1;
INSERT INTO "user" ("id", "email", "name", "password", "phone", "username", "is_enabled","authority") VALUES (1, NULL, '管理员', '$2a$10$RKauuiF5mFcUSE4QMbcZSOYIWa1gl3ayAhINHszZlpxJIBCLiR5Z6', NULL, 'root', true, 'ROLE_ADMIN');



