DROP TABLE IF EXISTS public.users;
CREATE TABLE  public.users(id text, userId text, email text, password text, displayName text);
CREATE INDEX idx_user_id ON public.users(userId);

DROP TABLE IF EXISTS public.shorts;
CREATE TABLE  public.shorts(id text, ownerId text, blobUrl text, timestamp int, totalLikes int);
CREATE INDEX idx_short_id ON public.shorts(id);