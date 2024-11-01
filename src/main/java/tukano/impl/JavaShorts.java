package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.ErrorCode.*;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.errorOrVoid;
import static tukano.api.Result.ok;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import tukano.api.Blobs;
import tukano.api.Result;
import tukano.api.Short;
import tukano.api.Shorts;
import tukano.api.User;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;
import tukano.impl.rest.TukanoRestServer;
import utils.Cache;
import utils.DB;

public class JavaShorts implements Shorts {

	private static Logger Log = Logger.getLogger(JavaShorts.class.getName());
	private static final String SHORT_FMT = "short:%s";
	private static final String USER_SHORTS_FMT = "user:%s:shorts";
	private static final String LIKE_FMT = "short:%s:likes";
	private static final String FOLLOWERS_FMT = "user:%s:followers";
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
				Cache.insertIntoCache(String.format(SHORT_FMT, s.getShortId()), s);
				Cache.appendList(String.format(USER_SHORTS_FMT, userId), s);
				Cache.appendList(String.format(FEED_FMT, userId), s);
				List<String> followers = followers(user.getUserId(), user.getPwd()).value();
				for (String followerId : followers) {
					String feedKey = String.format(FEED_FMT, followerId);
					Cache.appendList(feedKey, shrt);
				}
				return s.copyWithLikes_And_Token(0);
			});
		});
	}

	@Override
	public Result<Short> getShort(String shortId) {
		Log.info(() -> format("getShort : shortId = %s\n", shortId));

		if( shortId == null )
			return error(BAD_REQUEST);

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

	@Override
	public Result<Void> deleteShort(String shortId, String password) {
		Log.info(() -> format("deleteShort : shortId = %s, pwd = %s\n", shortId, password));

		Result<Short> s = Cache.getFromCache(String.format(SHORT_FMT, shortId), Short.class);
		if(!s.isOK()) s = DB.getOne(shortId, Short.class);

		return errorOrResult(s, shrt -> {

			return errorOrResult(okUser(shrt.getOwnerId(), password), user -> {
				if(!Cache.removeFromCache(String.format(SHORT_FMT, shortId)).isOK() ||
					!Cache.removeFromList(String.format(USER_SHORTS_FMT, shrt.getOwnerId()), shrt).isOK() ||
						!Cache.removeFromList(String.format(FEED_FMT, shrt.getOwnerId()), shrt).isOK())
					Log.info("Error deleting short from cache");

				if(!Cache.removeFromCache(String.format(LIKE_FMT, shortId)).isOK())
					Log.info("Error deleting short likes from cache");

				List<String> followers = followers(user.getUserId(), user.getPwd()).value();
				for (String followerId : followers) {
					String feedKey = String.format(FEED_FMT, followerId);
					Cache.removeFromList(feedKey, shrt);
				}

				var query = format("SELECT l FROM Likes l WHERE l.shortId = '%s'", shortId);
				var likesToDelete = DB.sql(query, Likes.class);

				likesToDelete.forEach(DB::deleteOne);

				// var blobsDeleted = JavaBlobs.getInstance().delete(shrt.getShortId(), Token.get(shrt.getShortId()));
				// if(!blobsDeleted.isOK()) return blobsDeleted;

				DB.deleteOne(shrt);
				return Result.ok();
			});
		});
	}

	@Override
	public Result<List<String>> getShorts(String userId) {
		Log.info(() -> format("getShorts : userId = %s\n", userId));

		String cacheKey = String.format(USER_SHORTS_FMT, userId);
		Result<List<Short>> shortsRes = Cache.getList(cacheKey, Short.class);
		List<Short> shorts = shortsRes.value();
		if(!Cache.isListCached(cacheKey)) {
			var query = format("SELECT * FROM Short s WHERE s.ownerId = '%s'", userId);
			shorts = DB.sql(query, Short.class);
			Cache.replaceList(cacheKey, shorts);
		}

		return errorOrValue(okUser(userId), shorts.stream().map(Short::getShortId).toList());
	}

	@Override
	public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
		Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2,
				isFollowing, password));

		return errorOrResult(okUser(userId1, password), user -> {
			var f = new Following(userId1, userId2);
			var res = errorOrVoid(okUser(userId2), isFollowing ? DB.insertOne(f) : DB.deleteOne(f));
			if(!res.isOK()) return res;

			if( isFollowing ) {
				Cache.appendList(String.format(FOLLOWERS_FMT, userId2), f.getFollower());
				Cache.appendList("following:" + userId1, f.getFollowee());
				List<Short> followeeShorts = Cache.getList(String.format(USER_SHORTS_FMT, userId2), Short.class).value();
				followeeShorts.forEach(s -> Cache.appendList(String.format(FEED_FMT, userId1), s));
			} else {
				Cache.removeFromList(String.format(FOLLOWERS_FMT, userId2), f.getFollower());
				Cache.removeFromList("following:" + userId1, f.getFollowee());
				List<Short> followeeShorts = Cache.getList(String.format(USER_SHORTS_FMT, userId2), Short.class).value();
				followeeShorts.forEach(s -> Cache.removeFromList(String.format(FEED_FMT, userId1), s));
			}
			return res;
		});
	}

	@Override
	public Result<List<String>> followers(String userId, String password) {
		Log.info(() -> format("followers : userId = %s, pwd = %s\n", userId, password));

		String cacheKey = String.format(USER_SHORTS_FMT, userId);
		List<String> followers = Cache.getList("followers:" + userId, String.class).value();
		if(!Cache.isListCached(cacheKey)) {
			var query = format("SELECT f.follower FROM Following f WHERE f.followee = '%s'", userId);
			followers = DB.sql(query, Following.class)
					.stream().map(Following::getFollower).collect(Collectors.toList());
			Cache.replaceList(cacheKey, followers);
		}
		return errorOrValue(okUser(userId, password), followers);
	}

	@Override
	public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
		Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked,
				password));

		return errorOrResult(getShort(shortId), shrt -> {
			String cacheKey = String.format(LIKE_FMT, shortId);
			var l = new Likes(userId, shortId, shrt.getOwnerId());
			var res = errorOrVoid(okUser(userId, password), isLiked ? DB.insertOne(l) : DB.deleteOne(l));
			if(!res.isOK()) return res;
			res = isLiked ? Cache.appendList(cacheKey, l.getUserId()) :
					Cache.removeFromList(cacheKey, l.getUserId());
			if(!res.isOK())
				Log.info("Error like from cache");

			return res;
		});
	}

	@Override
	public Result<List<String>> likes(String shortId, String password) {
		Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult(getShort(shortId), shrt -> {
			String cacheKey = String.format(LIKE_FMT, shortId);
			List<String> users = Cache.getList(cacheKey, String.class).value();
			if(!Cache.isListCached(cacheKey)) {
				var query = format("SELECT l.userId FROM Likes l WHERE l.shortId = '%s'", shortId);
				users = DB.sql(query, Likes.class)
						.stream().map(Likes::getUserId).collect(Collectors.toList());
				Cache.replaceList(cacheKey, users);
			}

			return errorOrValue(okUser(shrt.getOwnerId(), password), users);
		});
	}

	@Override
	public Result<List<String>> getFeed(String userId, String password) {
		Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

		String cacheKey = String.format(FEED_FMT, userId);
 		List<Short> cachedFeed = Cache.getList(String.format(FEED_FMT, userId), Short.class).value();
		if(Cache.isListCached(cacheKey)) {
			return errorOrValue(okUser(userId, password), cachedFeed
					.stream().map(s -> s.getShortId() + ", " + s.getTimestamp()).collect(Collectors.toList()));
		}

		final var QUERY_1_FMT = """
					SELECT * FROM Following f
					WHERE f.follower = '%s'
				""";

		var result = errorOrValue(okUser(userId, password), DB.sql(format(QUERY_1_FMT, userId), Following.class)
				.stream().map(Following::getFollowee).collect(Collectors.toList()));

		if(!result.isOK()) return result;

		var usersList = result.value();

		usersList.add(userId);

		String usersFormated = usersList.stream()
				.map(id -> "'" + id + "'")
				.collect(Collectors.joining(", "));

		final var QUERY_2_FMT = """
					SELECT * FROM Short s
					WHERE s.ownerId IN (%s)
					ORDER BY s.timestamp DESC
				""";

		return errorOrValue(okUser(userId, password), DB.sql(format(QUERY_2_FMT, usersFormated), Short.class)
				.stream().map(Short -> Short.getShortId() + ", " + Short.getTimestamp()).collect(Collectors.toList()));
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

		// delete shorts
		var query1 = format("SELECT * FROM Short s WHERE s.ownerId = '%s'", userId);
		var shortsToDelete = DB.sql(query1, Short.class);
		shortsToDelete.forEach(s -> deleteShort(s.getShortId(), password));

		// delete follows
		var query2 = format("SELECT * FROM Following f WHERE f.follower = '%s' OR f.followee = '%s'", userId, userId);
		var followsToDelete = DB.sql(query2, Following.class);
		followsToDelete.forEach(DB::deleteOne);

		// delete likes
		var query3 = format("SELECT * FROM Likes l WHERE l.ownerId = '%s' OR l.userId = '%s'", userId, userId);
		var likesToDelete = DB.sql(query3, Likes.class);
		likesToDelete.forEach(DB::deleteOne);

		return Result.ok();
	}

}