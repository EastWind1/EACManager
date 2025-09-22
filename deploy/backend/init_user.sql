DELETE
FROM "user"
WHERE "id" = 1;
INSERT INTO "user" ("id", "email", "name", "password", "phone", "username", "is_enabled", "authority")
VALUES (1, NULL, '管理员', '$2a$10$tEflRXoSbWxE5N347e2CheO/6oFtWR8XOtlQAZz3oP57hnf4bttCm', NULL, 'root', true,
        'ROLE_ADMIN');



