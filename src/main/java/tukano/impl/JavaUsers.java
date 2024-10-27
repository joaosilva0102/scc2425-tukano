package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.ErrorCode.*;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.ok;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import tukano.api.Result;
import tukano.api.User;
import tukano.api.Users;
import utils.DB;
import utils.JSON;
import utils.cache.RedisCache;

public class JavaUsers implements Users {

	private static Logger Log = Logger.getLogger(JavaUsers.class.getName());

	private static Users instance;

	synchronized public static Users getInstance() {
		if (instance == null)
			instance = new JavaUsers();
		return instance;
	}

	private JavaUsers() {
	}

	@Override
	public Result<String> createUser(User user) {
		Log.info(() -> format("createUser : %s\n", user));

		if (badUserInfo(user))
			return error(BAD_REQUEST);

		insertUserCache(user);

		return errorOrValue(DB.insertOne(user), user.getUserId());
	}

	@Override
	public Result<User> getUser(String userId, String pwd) {
		Log.info(() -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null)
			return error(BAD_REQUEST);

		Result<User> user = checkCache(userId);
		if (!user.isOK()) {
			user = DB.getOne(userId, User.class);
			if(user.isOK()) insertUserCache(user.value());
		}

		return validatedUserOrError(user, pwd);
	}

	@Override
	public Result<User> updateUser(String userId, String pwd, User other) {
		Log.info(() -> format("updateUser : userId = %s, pwd = %s, user: %s\n", userId, pwd, other));

		if (badUpdateUserInfo(userId, pwd, other))
			return error(BAD_REQUEST);

		Result<User> user = checkCache(userId);
		if(!user.isOK())
			user = DB.getOne(userId, User.class);

		return errorOrResult(validatedUserOrError(user, pwd),
				usr -> {
					if(!insertUserCache(other).isOK())
						return error(BAD_REQUEST);
					return DB.updateOne(usr.updateFrom(other));
				});
	}

	@SuppressWarnings("unchecked")
	@Override
	public Result<User> deleteUser(String userId, String pwd) {
		Log.info(() -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null || pwd == null)
			return error(BAD_REQUEST);

		Result<User> user = checkCache(userId);
		if (!user.isOK()) user = DB.getOne(userId, User.class);

		return errorOrResult(validatedUserOrError(user, pwd), usr -> {

			if(!removeCachedUser(userId).isOK())
				return error(BAD_REQUEST);

			// Delete user shorts and related info asynchronously in a separate thread
			Executors.defaultThreadFactory().newThread(() -> {
				JavaShorts.getInstance().deleteAllShorts(userId, pwd, Token.get(userId));
				JavaBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));
			}).start();

			return (Result<User>) DB.deleteOne(usr) ;
		});
	}

	@Override
	public Result<List<User>> searchUsers(String pattern) {
		Log.info(() -> format("searchUsers : patterns = %s\n", pattern));

		var query = format("SELECT * FROM User u WHERE UPPER(u.id) LIKE '%%%s%%'", pattern.toUpperCase());
		var hits = DB.sql(query, User.class)
				.stream()
				.map(User::copyWithoutPassword)
				.toList();

		return ok(hits);
	}

	private Result<User> validatedUserOrError(Result<User> res, String pwd) {
		if (res.isOK())
			return res.value().getPwd().equals(pwd) ? res : error(FORBIDDEN);
		else
			return res;
	}

	private boolean badUserInfo(User user) {
		return (user.userId() == null || user.pwd() == null || user.displayName() == null || user.email() == null);
	}

	private boolean badUpdateUserInfo(String userId, String pwd, User info) {
		return (userId == null || pwd == null || info.getUserId() != null && !userId.equals(info.getUserId()));
	}

	private Result<User> checkCache(String userId) {
		try (var jedis = RedisCache.getCachePool().getResource()) {
			var key = "user:" + userId;
			var value = jedis.get(key);
			if(value != null) {
				Log.info("Retrieved user from cache");
				return ok(JSON.decode(value, User.class));
			} else
				return error(NOT_FOUND);
		}
	}

	private Result<User> insertUserCache(User user) {
		try (var jedis = RedisCache.getCachePool().getResource()) {
			var key = "user:" + user.getUserId();
			var value = JSON.encode(user);
			jedis.set(key, value);
			Log.info("Inserted user into cache");
			return ok(user);
		}
	}

	private Result<Void> removeCachedUser(String userId) {
		try (var jedis = RedisCache.getCachePool().getResource()) {
			var key = "user:" + userId;
			jedis.del(key);
			Log.info("Removed user from cache");
			return ok();
		}
	}
}
