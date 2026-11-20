ALTER TABLE users
    ADD CONSTRAINT ck_users_username_min_length
    CHECK (char_length(username) >= 3);
