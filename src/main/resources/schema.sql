DROP TABLE IF EXISTS public.user;
CREATE TABLE  public.user(id text, userId text, email text, password text, displayName text);
CREATE INDEX idx_user_id ON public.user(userId);

DROP TABLE IF EXISTS public.short;
CREATE TABLE  public.short(id text, ownerId text, blobUrl text, timestamp int, totalLikes int);
CREATE INDEX idx_short_id ON public.short(id);