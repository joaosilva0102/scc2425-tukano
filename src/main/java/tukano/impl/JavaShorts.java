package tukano.impl;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import tukano.api.Short;
import tukano.api.*;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;
import tukano.impl.rest.TukanoRestServer;
import utils.GSON;
import utils.cache.Cache;
import utils.database.DB;

import java.net.URI;
import java.net.http.*;
import java.util.*;
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
    private static final Gson gson = new Gson();

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
                    Log.warning("Error inserting short into cache");
                return s.copyWithLikes_And_Token(0);
            });
        });
    }

    @Override
    public Result<Short> getShort(String shortId) {
        Log.info(() -> format("getShort : shortId = %s\n", shortId));

        if( shortId == null )
            return error(BAD_REQUEST);

        Result<Short> shrtRes = Cache.getFromCache(format(SHORT_FMT, shortId), Short.class);
        if(!shrtRes.isOK()) {
            shrtRes = DB.getOne(shortId, Short.class);
            if( !shrtRes.isOK() ) return error(NOT_FOUND);
            if(!Cache.insertIntoCache(format(SHORT_FMT, shortId), shrtRes.value()).isOK())
                Log.warning("Error inserting short into cache");
        }

        return errorOrValue(shrtRes, shrt -> shrt.copyWithLikes_And_Token(shrt.getTotalLikes()));
    }

    @Override
    public Result<Void> deleteShort(String shortId, String password) {
        Log.info(() -> format("deleteShort : shortId = %s, pwd = %s\n", shortId, password));

        Result<Short> s = Cache.getFromCache(format(SHORT_FMT, shortId), Short.class);
        if (!s.isOK())
            s = DB.getOne(shortId, Short.class);

        return errorOrResult(s, shrt -> errorOrResult(okUser(shrt.getOwnerId(), password), user -> {
            if (!removeCachedShort(shrt).isOK())
                return error(BAD_REQUEST);

            var query = format("SELECT * FROM likes l WHERE l.shortId = '%s'", shortId);
            List<Likes> likesToDelete = DB.sql(query, Likes.class);
            likesToDelete.forEach(DB::deleteOne);

            var blobsDeleted = JavaBlobs.getInstance().delete(shrt.getShortId(), Token.get(shrt.getShortId()));
            if (!blobsDeleted.isOK()) return blobsDeleted;

            DB.deleteOne(shrt);

            String tukanoShrtId = shrt.getShortId().replaceAll("^[^+]+", "tukano");

            Result<Short> newShort = Cache.getFromCache(format(SHORT_FMT, tukanoShrtId), Short.class);
            if (!newShort.isOK())
                newShort = DB.getOne(tukanoShrtId, Short.class);

            return errorOrResult(newShort, newShrt -> {
                if (!removeCachedShort(newShrt).isOK())
                    return error(BAD_REQUEST);

                var newQuery = format("SELECT * FROM likes l WHERE l.shortId = '%s'", tukanoShrtId);
                List<Likes> newLikesToDelete = DB.sql(newQuery, Likes.class);
                newLikesToDelete.forEach(DB::deleteOne);

                var newBlobsDeleted = JavaBlobs.getInstance().delete(newShrt.getShortId(), Token.get(newShrt.getShortId()));
                if (!newBlobsDeleted.isOK()) return newBlobsDeleted;

                DB.deleteOne(newShrt);
                return ok();
            });
        }));
    }

    @Override
    public Result<List<String>> getShorts(String userId) {
        Log.info(() -> format("getShorts : userId = %s\n", userId));

        String cacheKey = format(USER_SHORTS_FMT, userId);
        List<Short> shorts = Cache.getList(cacheKey, Short.class).value();
        if(!Cache.isListCached(cacheKey)) {
            var query = format("SELECT * FROM shorts s WHERE s.ownerId = '%s'", userId);
            shorts = DB.sql(query, Short.class);

            if(!Cache.replaceList(cacheKey, shorts).isOK())
                Log.warning("Error replacing user's shorts into cache");
        }

        return errorOrValue(okUser(userId), shorts.stream().map(Short::getShortId).toList());
    }

    @Override
    public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
        Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2,
                isFollowing, password));

        return errorOrResult(okUser(userId1, password), user -> {
            var f = new Following(userId1, userId2);
            Result<Void> res = errorOrVoid(okUser(userId2), isFollowing ? DB.insertOne(f) : DB.deleteOne(f));

            if(!res.isOK()) return res;

            List<Short> followeeShorts = Cache.getList(format(USER_SHORTS_FMT, userId2), Short.class).value();
            if( isFollowing )
                followeeShorts.forEach(s -> Cache.appendList(format(FEED_FMT, userId1), s));
            else
                followeeShorts.forEach(s -> Cache.removeFromList(format(FEED_FMT, userId1), s));

            return res;
        });
    }

    @Override
    public Result<List<String>> followers(String userId, String password) {
        Log.info(() -> format("followers : userId = %s, pwd = %s\n", userId, password));

        var query = format("SELECT * FROM following f WHERE f.followee = '%s'", userId);

        return errorOrValue(okUser(userId, password), DB.sql(query, Following.class)
                    .stream().map(Following::getFollower).collect(Collectors.toList()));
    }

    @Override
    public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
        Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked,
                password));

        return errorOrResult(getShort(shortId), shrt -> {
            var l = new Likes(userId, shortId, shrt.getOwnerId());

            return errorOrVoid(okUser(userId, password), isLiked ? changeLikes(shrt,true,l) : changeLikes(shrt,false,l));
        });
    }

    private Result<Likes> changeLikes(Short shrt, boolean isLiked, Likes l) {
        if(isLiked){
            shrt.setTotalLikes(shrt.getTotalLikes() + 1);
            DB.updateOne(shrt);
            var shrtCache = Cache.isCached(format(SHORT_FMT, shrt.getShortId()));
            if(shrtCache) {
                Cache.removeFromCache(format(SHORT_FMT, shrt.getShortId()));
                Cache.insertIntoCache(format(SHORT_FMT, shrt.getShortId()), shrt);
            }
            return DB.insertOne(l);
        }
        else{
            shrt.setTotalLikes(shrt.getTotalLikes() - 1);
            DB.updateOne(shrt);
            var shrtCache = Cache.isCached(format(SHORT_FMT, shrt.getShortId()));
            if(shrtCache) {
                Cache.removeFromCache(format(SHORT_FMT, shrt.getShortId()));
                Cache.insertIntoCache(format(SHORT_FMT, shrt.getShortId()), shrt);
            }
            return DB.deleteOne(l);
        }
    }

    @Override
    public Result<List<String>> likes(String shortId, String password) {
        Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

        return errorOrResult(getShort(shortId), shrt -> {
            var query = format("SELECT * FROM likes l WHERE l.shortId = '%s'", shortId);

            return errorOrValue(okUser(shrt.getOwnerId(), password), DB.sql(query, Likes.class)
                        .stream().map(Likes::getUserId).collect(Collectors.toList()));
        });
    }

    @Override
    public Result<List<String>> getFeed(String userId, String password) {
        Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

        Result<List<Short>> res = tukanoRecommends();
        if(!res.isOK()) {
            Log.severe("Error while retrieving tukano recommends shorts");
            return Result.error(INTERNAL_ERROR);
        }
        List<Short> shorts = res.value();
        Log.info(() -> format("Shorts size  : %d\n", shorts.size()));

        String cacheKey = format(FEED_FMT, userId);
        List<Short> cachedFeed = Cache.getList(format(FEED_FMT, userId), Short.class).value();
        for(Short s : shorts)
            if(!cachedFeed.contains(s)) {
                Cache.removeFromCache(cacheKey);
                break;
            }

        if(Cache.isListCached(cacheKey)) {
            List<Short> sortedFeed = new ArrayList<>(cachedFeed);
            sortedFeed.sort(Comparator.comparing(Short::getTimestamp).reversed());
            return errorOrValue(okUser(userId, password), sortedFeed
                    .stream().map(s -> s.getShortId() + ", " + s.getTimestamp()).collect(Collectors.toList()));
        }

        final var QUERY_1_FMT = """
					SELECT * FROM following f
					WHERE f.follower = '%s'
				""";
        Result<List<String>> result = errorOrValue(okUser(userId, password),
                DB.sql(format(QUERY_1_FMT, userId), Following.class)
                .stream().map(Following::getFollowee).collect(Collectors.toList()));

        if(!result.isOK()) return result;

        var usersList = result.value();
        usersList.add(userId);

        for (String user : usersList)
            Log.info(() -> format("User in follow list: %s\n", user));

        String usersFormated = usersList.stream()
                .map(id -> "'" + id + "'")
                .collect(Collectors.joining(", "));

        final var QUERY_2_FMT = """
					SELECT * FROM shorts s
					WHERE s.ownerId IN (%s)
					ORDER BY s.timestamp DESC
				""";
        List<Short> feed = DB.sql(format(QUERY_2_FMT, usersFormated), Short.class);
        if(!Cache.replaceList(format(FEED_FMT, userId), feed).isOK())
            Log.warning(format("Error updating %s feed into cache", userId));


        return errorOrValue(okUser(userId, password), feed.stream()
                .map(Short -> Short.getShortId() + ", " + Short.getTimestamp()).collect(Collectors.toList()));
    }

    protected Result<User> okUser(String userId, String pwd) {
        return JavaUsers.getInstance().getUser(userId, pwd);
    }

    private Result<Void> okUser(String userId) {
        var res = okUser(userId, "");
        if (res.error() == FORBIDDEN)
            return ok();
        else
            return error(res.error());
    }

    @Override
    public Result<Void> deleteAllShorts(String userId, String password, String token) {
        Log.info(() -> format("deleteAllShorts : userId = %s, password = %s, token = %s\n", userId, password, token));

        if (!Token.isValid(token, userId))
            return error(FORBIDDEN);

        // delete user shorts from db and cache
        List<Short> shortsToDelete = Cache.getList(format(USER_SHORTS_FMT, userId), Short.class).value();
        if(!Cache.isCached(format(USER_SHORTS_FMT, userId))) {
            var query1 = format("SELECT * FROM shorts s WHERE s.ownerId = '%s'", userId);
            shortsToDelete = DB.sql(query1, Short.class);
        }

        // get user shorts republished by tukano recommends
        List<Short> tukanoShorts = Cache.getList(format(USER_SHORTS_FMT, "Tukano"), Short.class).value();
        if (!Cache.isCached(format(USER_SHORTS_FMT, "Tukano"))) {
            var query2 = "SELECT * FROM shorts s WHERE s.ownerId = 'Tukano'";
            tukanoShorts = DB.sql(query2, Short.class);
        }

        shortsToDelete.forEach(s -> {
            removeCachedShort(s);
            DB.deleteOne(s);
        });

        // delete tukano recommends republished shorts
        tukanoShorts.forEach(s -> {
            removeCachedShort(s);
            DB.deleteOne(s);
        });

        // removes from cache list of user shorts
        Cache.removeFromCache(format(USER_SHORTS_FMT, userId));

        // delete follows
        var query2 = format("SELECT * FROM following f WHERE f.follower = '%s' OR f.followee = '%s'", userId, userId);
        var followsToDelete = DB.sql(query2, Following.class);
        followsToDelete.forEach(DB::deleteOne);

        // delete likes
        var query3 = format("SELECT * FROM likes l WHERE l.ownerId = '%s' OR l.userId = '%s'", userId, userId);
        List<Likes> likesToDelete;
        likesToDelete = DB.sql(query3, Likes.class);
        likesToDelete.forEach(DB::deleteOne);

        return ok();
    }

    /**
     * Inserts into cache a short
     * @param shrt short object to insert into cache
     * @param password of the owner of the short
     * @return the result of the operation
     */
    private Result<Void> insertShortToCache(Short shrt, String password) {
        try {
            Cache.insertIntoCache(format(SHORT_FMT, shrt.getShortId()), shrt);
            Cache.appendList(format(USER_SHORTS_FMT, shrt.getOwnerId()), shrt);
            List<String> followers = followers(shrt.getOwnerId(), password).value();
            followers.add(shrt.getOwnerId());
            for (String followerId : followers) {
                String feedKey = format(FEED_FMT, followerId);
                Cache.appendList(feedKey, shrt);
            }
            return ok();
        } catch (Exception e) {
            return error(BAD_REQUEST);
        }
    }

    /**
     * Removes from cache all occurrences of a short
     * @param shrt short object to remove from cache
     * @return the result of the operation
     */
    private Result<Void> removeCachedShort(Short shrt) {
        try {
            if(!Cache.removeFromCache(format(SHORT_FMT, shrt.getShortId())).isOK() ||
                    !Cache.removeFromList(format(USER_SHORTS_FMT, shrt.getOwnerId()), shrt).isOK() ||
                    !Cache.removeFromList(format(FEED_FMT, shrt.getOwnerId()), shrt).isOK())
                Log.info("Error deleting short from cache");

            var query2 = format("SELECT * FROM Following f WHERE f.followee = '%s'", shrt.getOwnerId());
            var followsToDelete = DB.sql(query2, Following.class);
            for (Following follower : followsToDelete) {
                String feedKey = format(FEED_FMT, follower.getFollower());
                Cache.removeFromList(feedKey, shrt);
            }
            return ok();
        } catch (Exception e) {
            return error(BAD_REQUEST);
        }
    }

    /**
     * Method to update Tukano Recommends shorts
     * @return the result of the operation
     */
    private Result<List<Short>> tukanoRecommends() {
        Result<Object> res = callTukanoRecommends();
        if (!res.isOK())
            return error(res.error());
        String reshorts = (String) res.value();//returns JSON
        if (reshorts == null) {
            Log.severe("Error calling tukanoRecommends");
            return error(INTERNAL_ERROR);
        }
        Log.info(() -> "Type of res.value(): " + (res.value() != null ? res.value().getClass().getName() : "null"));
        List<Short> shorts = gson.fromJson(reshorts, new TypeToken<List<Short>>() {}.getType());
        return ok(shorts);
    }

    /**
     * Calls the Serverless Azure function TukanoRecommends using a HTTP request
     * @return the result of the operation
     */
    private Result<Object> callTukanoRecommends() {
        String url = System.getProperty("TUKANO_RECOMMENDS_URL");

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
                return error(INTERNAL_ERROR);

            return ok(response.body());
        } catch (Exception e) {
            Log.severe("Error while calling HTTP trigger function: " + e.getMessage());
            return error(INTERNAL_ERROR);
        }
    }
}