DROP TABLE IF EXISTS public.user;
CREATE TABLE  public.user(userId text, email text, password text, displayName text);
CREATE INDEX idx_user_id ON public.user(userId);

DROP TABLE IF EXISTS public.short;
CREATE TABLE  public.short(shortId text, ownerId text, blobUrl text, timestamp int, totalLikes int);
CREATE INDEX idx_short_id ON public.short(shortId);