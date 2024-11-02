DROP TABLE IF EXISTS public.users;
CREATE TABLE  public.users(id text, userId text, email text, password text, displayName text);
ALTER TABLE users ADD CONSTRAINT unique_user_id UNIQUE (userId);
CREATE INDEX idx_user_id ON public.users(userId);

DROP TABLE IF EXISTS public.short;
CREATE TABLE  public.short(id text, ownerId text, blobUrl text, timestamp int, totalLikes int);
CREATE INDEX idx_short_id ON public.short(id);