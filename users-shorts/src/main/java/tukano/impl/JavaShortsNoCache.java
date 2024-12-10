package tukano.impl;

import jakarta.ws.rs.core.Cookie;
import tukano.api.*;
import tukano.api.Short;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;
import tukano.impl.rest.TukanoRestServer;
import utils.Result;
import utils.Token;
import utils.database.DB;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static auth.Authentication.COOKIE_KEY;
import static java.lang.String.format;
import static utils.Result.ErrorCode.*;
import static utils.Result.*;

public class JavaShortsNoCache implements Shorts {

    private static final Logger Log = Logger.getLogger(JavaShortsNoCache.class.getName());
    private static final String BLOBS_NAME = "blobs";

    private static Shorts instance;

    synchronized public static Shorts getInstance() {
        if( instance == null )
            instance = new JavaShortsNoCache();
        return instance;
    }

    private JavaShortsNoCache() {}

    @Override
    public utils.Result<Short> createShort(String userId, String password) {
        Log.info(() -> format("createShort : userId = %s, pwd = %s\n", userId, password));

        return errorOrResult( okUser(userId, password), user -> {
            var shortId = format("%s+%s", userId, UUID.randomUUID());
            var blobUrl = format("%s/%s/%s", TukanoRestServer.serverURI, BLOBS_NAME, shortId);
            var shrt = new Short(shortId, userId, blobUrl);

            return errorOrValue(DB.insertOne(shrt), s -> s.copyWithLikes_And_Token(0));
        });
    }

    @Override
    public utils.Result<Short> getShort(String shortId) {
        Log.info(() -> format("getShort : shortId = %s\n", shortId));

        if( shortId == null )
            return error(BAD_REQUEST);

        utils.Result<Short> shrtRes = DB.getOne(shortId, Short.class);
        if( !shrtRes.isOK() ) return error(NOT_FOUND);

        var query = format("SELECT * FROM Likes l WHERE l.shortId = '%s'", shortId);
        var likes = DB.sql(query, Likes.class).size();

        return errorOrValue(shrtRes, shrt -> shrt.copyWithLikes_And_Token(likes));
    }

    @Override
    public utils.Result<Void> deleteShort(String shortId, String password, Cookie cookie) {
        Log.info(() -> format("deleteShort : shortId = %s, pwd = %s\n", shortId, password));

        utils.Result<Short> s = DB.getOne(shortId, Short.class);

        return errorOrResult(s, shrt -> errorOrResult(okUser(shrt.getOwnerId(), password), user -> {

            var query = format("SELECT * FROM likes l WHERE l.shortId = '%s'", shortId);
            List<Likes> likesToDelete = DB.sql(query, Likes.class);
            likesToDelete.forEach(DB::deleteOne);

            Result<Void> blobsDeleted;
            try {
                blobsDeleted = deleteShortBlob(shrt.getShortId(), cookie);
                if (!blobsDeleted.isOK()) Log.warning("No blob found associated with this short");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            DB.deleteOne(shrt);
            return ok();
        }));
    }

    @Override
    public utils.Result<List<String>> getShorts(String userId) {
        Log.info(() -> format("getShorts : userId = %s\n", userId));

        var query = format("SELECT * FROM shorts s WHERE s.ownerId = '%s'", userId);
        List<Short> shorts = DB.sql(query, Short.class);

        return errorOrValue(okUser(userId), shorts.stream().map(Short::getShortId).toList());
    }

    @Override
    public utils.Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
        Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2,
                isFollowing, password));

        return errorOrResult(okUser(userId1, password), user -> {
            var f = new Following(userId1, userId2);

            return errorOrVoid(okUser(userId2), isFollowing ? DB.insertOne(f) : DB.deleteOne(f));
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
        Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked, password));


        return errorOrResult( getShort(shortId), shrt -> {
            var l = new Likes(userId, shortId, shrt.getOwnerId());
            return errorOrVoid( okUser( userId, password), isLiked ? DB.insertOne( l ) : DB.deleteOne( l ));
        });
    }

    @Override
    public utils.Result<List<String>> likes(String shortId, String password) {
        Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

        return errorOrResult( getShort(shortId), shrt -> {

            var query = format("SELECT l.userId FROM Likes l WHERE l.shortId = '%s'", shortId);

            return errorOrValue( okUser( shrt.getOwnerId(), password ), DB.sql(query, Likes.class)
                    .stream().map(Likes::getUserId).collect(Collectors.toList()));
        });
    }

    @Override
    public utils.Result<List<String>> getFeed(String userId, String password) {
        Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

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

        String usersFormated = usersList.stream()
                .map(id -> "'" + id + "'")
                .collect(Collectors.joining(", "));

        final var QUERY_2_FMT = """
					SELECT * FROM shorts s
					WHERE s.ownerId IN (%s)
					ORDER BY s.timestamp DESC
				""";

        List<Short> feed = DB.sql(format(QUERY_2_FMT, usersFormated), Short.class);

        return errorOrValue(okUser(userId, password), feed.stream()
                .map(Short -> Short.getShortId() + ", " + Short.getTimestamp()).collect(Collectors.toList()));
    }

    protected utils.Result<User> okUser(String userId, String pwd) {
        return JavaUsersNoCache.getInstance().getUser(userId, pwd);
    }

    private utils.Result<Void> okUser(String userId) {
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

        // delete user shorts from db
        var query1 = format("SELECT * FROM shorts s WHERE s.ownerId = '%s'", userId);
        List<Short> shortsToDelete =  DB.sql(query1, Short.class);
        shortsToDelete.forEach(DB::deleteOne);

        // delete follows
        var query3 = format("SELECT * FROM following f WHERE f.follower = '%s' OR f.followee = '%s'", userId, userId);
        List<Following> followsToDelete = DB.sql(query3, Following.class);
        followsToDelete.forEach(DB::deleteOne);

        // delete likes
        var query4 = format("SELECT * FROM likes l WHERE l.ownerId = '%s' OR l.userId = '%s'", userId, userId);
        List<Likes> likesToDelete = DB.sql(query4, Likes.class);
        likesToDelete.forEach(DB::deleteOne);

        return ok();
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
}