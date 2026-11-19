-- When the password last changed. Tokens are refused for a login made before it, so changing the password ends
-- every other login, including auth-service's own login sessions, which deleting authorizations alone can't reach.
-- NULL: never changed since the account was created.
ALTER TABLE users ADD COLUMN password_changed_at timestamptz;
