package tukano.impl;

import tukano.api.Short;
import tukano.api.*;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;
import tukano.impl.rest.TukanoRestServer;
import utils.Cache;
import utils.DB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static tukano.api.Result.ErrorCode.*;
import static tukano.api.Result.*;

public class JavaShorts implements Shorts {

	private static final Logger Log = Logger.getLogger(JavaShorts.class.getName());
	private static final String SHORT_FMT = "short:%s";
	private static final String USER_SHORTS_FMT = "user:%s:shorts";
	private static final String FEED_FMT = "user:%s:feed";

	private static Shorts instance;

	synchronized public static Shorts getInstance() {
		if( instance == null )
			instance = new JavaShorts();
		return instance;
	}

	private JavaShorts() {}

	@Override
	public Result<Short> createShort(String userId, String password) {
		Log.info(() -> format("createShort : userId = %s, pwd = %s\n", userId, password));

		return errorOrResult( okUser(userId, password), user -> {
			var shortId = format("%s+%s", userId, UUID.randomUUID());
			var blobUrl = format("%s/%s/%s", TukanoRestServer.serverURI, Blobs.NAME, shortId); 
			var shrt = new Short(shortId, userId, blobUrl);

			return errorOrValue(DB.insertOne(shrt), s -> {
				if(!insertShortToCache(s, password).isOK())
					Log.info("Error inserting short into cache");
				return s.copyWithLikes_And_Token(0);
			});
		});
	}


    @Override
    public Result<Short> createShort(String userId, String password) {
        Log.info(() -> format("createShort : userId = %s, pwd = %s\n", userId, password));

		Result<Short> shrtRes = Cache.getFromCache(String.format(SHORT_FMT, shortId), Short.class);
		if(!shrtRes.isOK()) {
			shrtRes = DB.getOne(shortId, Short.class);
			if( !shrtRes.isOK() ) return Result.error(NOT_FOUND);
			Cache.insertIntoCache(String.format(SHORT_FMT, shortId), shrtRes.value());
		}

		var query = format("SELECT * FROM Likes l WHERE l.shortId = '%s'", shortId); // TODO Cache likes (?) Maybe create Az Function to update likes
		var likes = DB.sql(query, Likes.class).size();
		return errorOrValue(shrtRes, shrt -> shrt.copyWithLikes_And_Token(likes));
	}

            Cache.insertIntoCache("short", shortId, shrt);

		Result<Short> s = Cache.getFromCache(String.format(SHORT_FMT, shortId), Short.class);
		if(!s.isOK()) s = DB.getOne(shortId, Short.class);

		return errorOrResult(s, shrt -> errorOrResult(okUser(shrt.getOwnerId(), password), user -> {
            if(!removeCachedShort(shrt, password).isOK())
                return Result.error(BAD_REQUEST);

            var query = format("SELECT l FROM Likes l WHERE l.shortId = '%s'", shortId);
            var likesToDelete = DB.sql(query, Likes.class);

            likesToDelete.forEach(DB::deleteOne);

            // var blobsDeleted = JavaBlobs.getInstance().delete(shrt.getShortId(), Token.get(shrt.getShortId()));
            // if(!blobsDeleted.isOK()) return blobsDeleted;

            DB.deleteOne(shrt);
            return Result.ok();
        }));
	}

        }
//            Log.log(Level.WARNING, "AQUII: " + cacheRes);
//            return errorOrValue(cacheRes.isOK() ? cacheRes : nosql ? DB.getOne(shortId, Short.class) : CosmosPostgresDB.getOne(shortId, Short.class), shrt -> shrt.copyWithLikes_And_Token(likes));
        return result;
    }

		String cacheKey = String.format(USER_SHORTS_FMT, userId);
		List<Short> shorts = Cache.getList(cacheKey, Short.class).value();
		if(!Cache.isListCached(cacheKey)) {
			var query = format("SELECT * FROM Short s WHERE s.ownerId = '%s'", userId);
			shorts = DB.sql(query, Short.class);
			Cache.replaceList(cacheKey, shorts);
		}

		return errorOrValue(okUser(userId), shorts.stream().map(Short::getShortId).toList());
	}

        return errorOrResult(getShort(shortId), shrt -> {

		return errorOrResult(okUser(userId1, password), user -> {
			var f = new Following(userId1, userId2);
			var res = errorOrVoid(okUser(userId2), isFollowing ? DB.insertOne(f) : DB.deleteOne(f));
			if(!res.isOK()) return res;

            List<Short> followeeShorts = Cache.getList(String.format(USER_SHORTS_FMT, userId2), Short.class).value();
            if( isFollowing ) {
                followeeShorts.forEach(s -> Cache.appendList(String.format(FEED_FMT, userId1), s));
			} else {
                followeeShorts.forEach(s -> Cache.removeFromList(String.format(FEED_FMT, userId1), s));
			}

			return res;
		});
	}

                if (nosql) {
                    DB.deleteOne(shrt);
                } else {
                    PostgreDB.deleteOne(shrt);
                }
                Cache.removeFromCache("short", shortId);
                Log.info(() -> format("Deleted short %s\n", shortId));
                var query = format("SELECT l FROM Likes l WHERE l.shortId = '%s'", shortId);
                String query2 = "SELECT * FROM Likes WHERE shortId = '%s'";
                List<Likes> itemsToDelete;

                if (nosql) {
                    itemsToDelete = DB.sql(query, Likes.class);
                } else {
                    try {
                        //itemsToDelete = PostgreDB.sql(Likes.class, query2);
                        itemsToDelete = PostgreDB.sql(Likes.class, query2, shortId);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                for (Likes like : itemsToDelete) {
                    if (nosql) {
                        DB.deleteOne(like);
                    } else {
                        PostgreDB.deleteOne(like);
                    }
                }

		return errorOrResult(getShort(shortId), shrt -> {
			var l = new Likes(userId, shortId, shrt.getOwnerId());

			return errorOrVoid(okUser(userId, password), isLiked ? DB.insertOne(l) : DB.deleteOne(l));
		});
	}

                return Result.ok();
            });
        });
    }

		return errorOrResult(getShort(shortId), shrt -> {
			var query = format("SELECT l.userId FROM Likes l WHERE l.shortId = '%s'", shortId);

    @Override
    public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
        Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2,
                isFollowing, password));

        return errorOrResult(okUser(userId1, password), user -> {
            var f = new Following(userId1, userId2);
            if (nosql)
                return errorOrVoid(okUser(userId2), isFollowing ? DB.insertOne(f) : DB.deleteOne(f));
            else {
                return errorOrVoid(okUser(userId2), isFollowing ? PostgreDB.insertOne(f) : PostgreDB.deleteOne(f));
            }
        });
    }

		String cacheKey = String.format(FEED_FMT, userId);
 		List<Short> cachedFeed = Cache.getList(String.format(FEED_FMT, userId), Short.class).value();
		if(Cache.isListCached(cacheKey)) {
			List<Short> sortedFeed = new ArrayList<>(cachedFeed);
			sortedFeed.sort(Comparator.comparing(Short::getTimestamp).reversed());
			return errorOrValue(okUser(userId, password), sortedFeed
					.stream().map(s -> s.getShortId() + ", " + s.getTimestamp()).collect(Collectors.toList()));
		}

		final var QUERY_1_FMT = """
					SELECT * FROM Following f
					WHERE f.follower = '%s'
				""";

        var query = format("SELECT f.follower FROM Following f WHERE f.followee = '%s'", userId);
        var query2 = "SELECT * FROM Following WHERE followee = '%s'";
        if (nosql)
            return errorOrValue(okUser(userId, password), DB.sql(query, Following.class)
                    .stream().map(Following::getFollower).collect(Collectors.toList()));
        else {
            try {
                return errorOrValue(okUser(userId, password), PostgreDB.sql(Following.class, query2, userId)
                        .stream().map(Following::getFollower).collect(Collectors.toList()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
        Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked,
                password));

        if (nosql) {
            return errorOrResult(getShort(shortId), shrt -> {
                var l = new Likes(userId, shortId, shrt.getOwnerId());
                return errorOrVoid(okUser(userId, password), isLiked ? DB.insertOne(l) : DB.deleteOne(l));
            });
        } else {
            return errorOrResult(getShort(shortId), shrt -> {
                var l = new Likes(userId, shortId, shrt.getOwnerId());
                return errorOrVoid(okUser(userId, password), isLiked ? PostgreDB.insertOne(l) : PostgreDB.deleteOne(l));
            });

        }

    }

    @Override
    public Result<List<String>> likes(String shortId, String password) {
        Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

		List<String> feed = DB.sql(format(QUERY_2_FMT, usersFormated), Short.class)
				.stream().map(Short -> Short.getShortId() + ", " + Short.getTimestamp()).collect(Collectors.toList());

		Cache.replaceList(String.format(FEED_FMT, userId), feed);

		return errorOrValue(okUser(userId, password), feed);
	}

            var query = format("SELECT l.userId FROM Likes l WHERE l.shortId = '%s'", shortId);
            var query2 = "SELECT * FROM Likes WHERE shortId = '%s'";

            if (nosql)
                return errorOrValue(okUser(shrt.getOwnerId(), password), DB.sql(query, Likes.class)
                        .stream().map(Likes::getUserId).collect(Collectors.toList()));
            else {
                try {
                    Log.info("USER:   " + okUser(shrt.getOwnerId(), password).toString());
                    return errorOrValue(okUser(shrt.getOwnerId(), password), PostgreDB.sql(Likes.class, query2, shortId)
                            .stream().map(Likes::getUserId).collect(Collectors.toList()));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Override
    public Result<List<String>> getFeed(String userId, String password) {
        Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

        final var QUERY_1_FMT = """
                	SELECT * FROM Following f
                	WHERE f.follower = '%s'
                """;
        Result<List<String>> result;
        if (nosql)
            result = errorOrValue(okUser(userId, password), DB.sql(format(QUERY_1_FMT, userId), Following.class)
                    .stream().map(Following::getFollowee).collect(Collectors.toList()));
        else {
            try {
                result = errorOrValue(okUser(userId, password), PostgreDB.sql(Following.class, format(QUERY_1_FMT, userId))
                        .stream().map(Following::getFollowee).collect(Collectors.toList()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

		// delete shorts
		List<Short> shortsToDelete = Cache.getList(String.format(USER_SHORTS_FMT, userId), Short.class).value();
		if(!Cache.isCached(String.format(USER_SHORTS_FMT, userId))) {
			var query1 = format("SELECT * FROM Short s WHERE s.ownerId = '%s'", userId);
			shortsToDelete = DB.sql(query1, Short.class);
		}
		shortsToDelete.forEach(s -> {
			removeCachedShort(s, password);
			DB.deleteOne(s);
		});

		// delete follows
		var query2 = format("SELECT * FROM Following f WHERE f.follower = '%s' OR f.followee = '%s'", userId, userId);
		var followsToDelete = DB.sql(query2, Following.class);
		followsToDelete.forEach(DB::deleteOne);

		// delete likes
		var query3 = format("SELECT * FROM Likes l WHERE l.ownerId = '%s' OR l.userId = '%s'", userId, userId);
		var likesToDelete = DB.sql(query3, Likes.class);
		likesToDelete.forEach(DB::deleteOne);

        usersList.add(userId);

	private Result<Void> insertShortToCache(Short shrt, String password) {
		try {
			Cache.insertIntoCache(String.format(SHORT_FMT, shrt.getShortId()), shrt);
			Cache.appendList(String.format(USER_SHORTS_FMT, shrt.getOwnerId()), shrt);
			List<String> followers = followers(shrt.getOwnerId(), password).value();
			followers.add(shrt.getOwnerId());
			for (String followerId : followers) {
				String feedKey = String.format(FEED_FMT, followerId);
				Cache.appendList(feedKey, shrt);
			}
		} catch (Exception e) {
			return Result.error(BAD_REQUEST);
		}
		return ok();
	}

	private Result<Void> removeCachedShort(Short shrt, String password) {
		try {
			if(!Cache.removeFromCache(String.format(SHORT_FMT, shrt.getShortId())).isOK() ||
					!Cache.removeFromList(String.format(USER_SHORTS_FMT, shrt.getOwnerId()), shrt).isOK() ||
					!Cache.removeFromList(String.format(FEED_FMT, shrt.getOwnerId()), shrt).isOK())
				Log.info("Error deleting short from cache");

			var query2 = format("SELECT * FROM Following f WHERE f.followee = '%s'", shrt.getOwnerId());
			var followsToDelete = DB.sql(query2, Following.class);
			for (Following follower : followsToDelete) {
				String feedKey = String.format(FEED_FMT, follower.getFollower());
				Cache.removeFromList(feedKey, shrt);
			}
		} catch (Exception e) {
			return Result.error(BAD_REQUEST);
		}
		return ok();
	}
}