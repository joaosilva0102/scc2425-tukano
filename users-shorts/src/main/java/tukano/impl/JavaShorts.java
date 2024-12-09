package tukano.impl;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.NewCookie;
import tukano.api.*;
import tukano.api.Short;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;
import tukano.impl.rest.TukanoRestServer;
import utils.Result;
import utils.Token;
import utils.cache.Cache;
import utils.cache.RedisCache;
import utils.database.DB;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static auth.Authentication.COOKIE_KEY;
import static java.lang.String.format;
import static utils.Result.ErrorCode.*;
import static utils.Result.*;

public class JavaShorts implements Shorts {

    private static final Logger Log = Logger.getLogger(JavaShorts.class.getName());
    private static final String SHORT_FMT = "short:%s";
    private static final String USER_SHORTS_FMT = "user:%s:shorts";
    private static final String FEED_FMT = "user:%s:feed";
    private static final Gson gson = new Gson();
    private static final String BLOBS_NAME = "blobs";

    private static Shorts instance;

    synchronized public static Shorts getInstance() {
        if( instance == null )
            instance = new JavaShorts();
        return instance;
    }

    private JavaShorts() {}

    @Override
    public utils.Result<Short> createShort(String userId, String password) {
        Log.info(() -> format("createShort : userId = %s, pwd = %s\n", userId, password));

        return errorOrResult( okUser(userId, password), user -> {
            var shortId = format("%s+%s", userId, UUID.randomUUID());
            var blobUrl = format("%s/%s/%s", TukanoRestServer.serverURI, BLOBS_NAME, shortId);
            var shrt = new Short(shortId, userId, blobUrl);

            return errorOrValue(DB.insertOne(shrt), s -> {
                if(!insertShortToCache(s, password).isOK())
                    Log.warning("Error inserting short into cache");
                return s.copyWithLikes_And_Token(0);
            });
        });
    }

    @Override
    public utils.Result<Short> getShort(String shortId) {
        Log.info(() -> format("getShort : shortId = %s\n", shortId));

        if( shortId == null )
            return error(BAD_REQUEST);

        utils.Result<Short> shrtRes = Cache.getFromCache(format(SHORT_FMT, shortId), Short.class);
        if(!shrtRes.isOK()) {
            shrtRes = DB.getOne(shortId, Short.class);
            if( !shrtRes.isOK() ) return error(NOT_FOUND);
            if(!Cache.insertIntoCache(format(SHORT_FMT, shortId), shrtRes.value()).isOK())
                Log.warning("Error inserting short into cache");
        }

        return errorOrValue(shrtRes, shrt -> shrt.copyWithLikes_And_Token(shrt.getTotalLikes()));
    }

    @Override
    public utils.Result<Void> deleteShort(String shortId, String password, Cookie cookie) {
        Log.info(() -> format("deleteShort : shortId = %s, pwd = %s\n", shortId, password));

        utils.Result<Short> s = Cache.getFromCache(format(SHORT_FMT, shortId), Short.class);
        if (!s.isOK())
            s = DB.getOne(shortId, Short.class);

        return errorOrResult(s, shrt -> errorOrResult(okUser(shrt.getOwnerId(), password), user -> {
            if (!removeCachedShort(shrt).isOK())
                return error(BAD_REQUEST);

            var query = format("SELECT * FROM likes l WHERE l.shortId = '%s'", shortId);
            List<Likes> likesToDelete = DB.sql(query, Likes.class);
            likesToDelete.forEach(DB::deleteOne);

            Result<Void> blobsDeleted;
            if(!user.getUserId().equals("Tukano")) {
                try {
                    blobsDeleted = deleteShortBlob(shrt.getShortId(), cookie);
                    if (!blobsDeleted.isOK()) Log.warning("No blob found associated with this short");
                } catch (Exception e) {
                    Log.warning("User not authorized to delete the blob");
                }
            }

            DB.deleteOne(shrt);

            String tukanoShrtId = "tukano+" + shrt.getShortId();
            utils.Result<Short> newShort = Cache.getFromCache(format(SHORT_FMT, tukanoShrtId), Short.class);
            if (!newShort.isOK())
                newShort = DB.getOne(tukanoShrtId, Short.class);

            return errorOrResult(newShort, newShrt -> {
                if (!removeCachedShort(newShrt).isOK())
                    return error(BAD_REQUEST);

                var newQuery = format("SELECT * FROM likes l WHERE l.shortId = '%s'", tukanoShrtId);
                List<Likes> newLikesToDelete = DB.sql(newQuery, Likes.class);
                newLikesToDelete.forEach(DB::deleteOne);

                DB.deleteOne(newShrt);
                return ok();
            });
        }));
    }

    @Override
    public utils.Result<List<String>> getShorts(String userId) {
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
    public utils.Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
        Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2,
                isFollowing, password));

        return errorOrResult(okUser(userId1, password), user -> {
            var f = new Following(userId1, userId2);
            utils.Result<Void> res = errorOrVoid(okUser(userId2), isFollowing ? DB.insertOne(f) : DB.deleteOne(f));

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
    public utils.Result<List<String>> followers(String userId, String password) {
        Log.info(() -> format("followers : userId = %s, pwd = %s\n", userId, password));

        var query = format("SELECT * FROM following f WHERE f.followee = '%s'", userId);

        return errorOrValue(okUser(userId, password), DB.sql(query, Following.class)
                    .stream().map(Following::getFollower).collect(Collectors.toList()));
    }

    @Override
    public utils.Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
        Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked,
                password));

        return errorOrResult(getShort(shortId), shrt -> {
            var l = new Likes(userId, shortId, shrt.getOwnerId());

            return errorOrVoid(okUser(userId, password), usr -> changeLikes(shrt,isLiked,l));
        });
    }

    private utils.Result<Likes> changeLikes(Short shrt, boolean isLiked, Likes l) {
        utils.Result<Likes> res;
        if(isLiked){
            res = DB.insertOne(l);
            if(!res.isOK()) return res;
            shrt.setTotalLikes(shrt.getTotalLikes() + 1);
        }
        else{
            if(!DB.getOne(l.getId(), Likes.class).isOK())
                return utils.Result.error(NOT_FOUND);
            res = DB.deleteOne(l);
            shrt.setTotalLikes(shrt.getTotalLikes() - 1);
        }
        shrt.setBlobUrl(shrt.getBlobUrl().split("\\?token=")[0]);
        DB.updateOne(shrt);
        Cache.insertIntoCache(format(SHORT_FMT, shrt.getShortId()), shrt);

        return res;

    }

    @Override
    public utils.Result<List<String>> likes(String shortId, String password) {
        Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

        return errorOrResult(getShort(shortId), shrt -> {
            var query = format("SELECT * FROM likes l WHERE l.shortId = '%s'", shortId);

            return errorOrValue(okUser(shrt.getOwnerId(), password), DB.sql(query, Likes.class)
                        .stream().map(Likes::getUserId).collect(Collectors.toList()));
        });
    }

    @Override
    public utils.Result<List<String>> getFeed(String userId, String password) {
        Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));
        List<Short> recommendedShorts = new ArrayList<>();
        try {
            recommendedShorts = tukanoRecommends().value();
        } catch (Exception e) {
            Log.severe("Error retrieving tukano recommendationss");
        }

        String cacheKey = format(FEED_FMT, userId);
        List<Short> cachedFeed = Cache.getList(format(FEED_FMT, userId), Short.class).value();
        for(Short s : recommendedShorts)
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
        utils.Result<List<String>> result = errorOrValue(okUser(userId, password),
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

        //feed.addAll(recommendedShorts);
        //feed.sort(Comparator.comparing(Short::getTimestamp).reversed());
        return errorOrValue(okUser(userId, password), feed.stream()
                .map(Short -> Short.getShortId() + ", " + Short.getTimestamp()).collect(Collectors.toList()));
    }

    protected utils.Result<User> okUser(String userId, String pwd) {
        return JavaUsers.getInstance().getUser(userId, pwd);
    }

    private utils.Result<Void> okUser(String userId) {
        var res = okUser(userId, "");
        if (res.error() == FORBIDDEN)
            return ok();
        else
            return error(res.error());
    }

    @Override
    public utils.Result<Void> deleteAllShorts(String userId, String password, String token) {
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

        // Perform deletion of generic shorts first
        shortsToDelete.forEach(s -> {
            removeCachedShort(s);
            DB.deleteOne(s);
        });

        List<Short> shortsToRemove = new ArrayList<>();

        String specificPattern = "^tukano\\+" + userId + "\\+.*$";

        tukanoShorts.forEach(s -> {
            if (s.getShortId().matches(specificPattern)) {
                Log.info(() -> format("Marking tukano for user %s recommends short: %s for deletion\n", userId, s.getShortId()));
                shortsToRemove.add(s);
            }
        });

        shortsToRemove.forEach(s -> {
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
    private utils.Result<Void> insertShortToCache(Short shrt, String password) {
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
    private utils.Result<Void> removeCachedShort(Short shrt) {
        try {
            Log.info(() -> format("Removing short %s from cache and owner %s\n", shrt.getShortId(), shrt.getOwnerId()));
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

    private Result<Void> deleteShortBlob(String blobId, Cookie cookie) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://tukano-blobs-service:8080/rest/blobs/" + blobId + "?token=" + Token.get(blobId)))
                .header("Cookie", COOKIE_KEY + "=" + cookie.getValue())
                .DELETE()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if(response.statusCode() != 200)
            return error(BAD_REQUEST);
        return Result.ok();
    }

    private Result<List<Short>> tukanoRecommends() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://tukano-users-shorts-service:8080/rest/shorts/recommendations"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();
        Gson gson = new Gson();
        Type listType = new TypeToken<List<Short>>() {}.getType();
        List<Short> tukanoShorts = gson.fromJson(responseBody, listType);
        if(response.statusCode() != 200)
            return error(BAD_REQUEST);
        return Result.ok(tukanoShorts);
    }

}