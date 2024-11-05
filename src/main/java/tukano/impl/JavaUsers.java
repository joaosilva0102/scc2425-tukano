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
import utils.cache.Cache;
import utils.database.DB;
import utils.database.PostgresDB;

public class JavaUsers implements Users {

	private static final Logger Log = Logger.getLogger(JavaUsers.class.getName());
	private static Users instance;
	private static final String USERS_LIST = "USERS_LIST";
	private static final String USER_FMT = "user:%s";

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

		if (Cache.isCached(String.format(USER_FMT, user.getUserId())))
			return error(CONFLICT);

		Result<User> r = DB.insertOne(user);

		if(!Cache.insertIntoCache(String.format(USER_FMT, user.getUserId()), user).isOK() ||
				!Cache.appendList(USERS_LIST, user).isOK())
			Log.info("Error inserting user into cache");

		if(!user.getUserId().equals("Tukano")) {
			Log.info("Following Tukano:  " + user.getUserId());
			var result = JavaShorts.getInstance().follow(user.getUserId(), "Tukano", true, user.getPwd());
			Log.info("Result: " + result);
		}

		return errorOrValue(r, user.getUserId());
	}

	@Override
	public Result<User> getUser(String userId, String pwd) {
		Log.info(() -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null)
			return error(BAD_REQUEST);

		Result<User> user = Cache.getFromCache(String.format(USER_FMT, userId), User.class);
		if (!user.isOK()) {
			user = DB.getOne(userId, User.class);
			if(user.isOK()) Cache.insertIntoCache(String.format(USER_FMT, userId), user.value());
		}
		return validatedUserOrError(user, pwd);
	}

	@Override
	public Result<User> updateUser(String userId, String pwd, User other) {
		Log.info(() -> format("updateUser : userId = %s, pwd = %s, user: %s\n", userId, pwd, other));

		if (badUpdateUserInfo(userId, pwd, other))
			return error(BAD_REQUEST);

		Result<User> user = Cache.getFromCache(String.format(USER_FMT, userId), User.class);
		if(!user.isOK())
			user = DB.getOne(userId, User.class);

		return errorOrResult(validatedUserOrError(user, pwd),
				usr -> {
					User updatedUser = usr.updateFrom(other);
					if(!DB.updateOne(updatedUser).isOK())
						return error(BAD_REQUEST);

					Cache.updateList(USERS_LIST, usr, updatedUser);
					return Cache.insertIntoCache(String.format(USER_FMT, userId), updatedUser);
				});
	}


	@Override
	public Result<User> deleteUser(String userId, String pwd) {
		Log.info(() -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null || pwd == null)
			return error(BAD_REQUEST);

		Result<User> user = Cache.getFromCache(String.format(USER_FMT, userId), User.class);
		if (!user.isOK())
				user = DB.getOne(userId, User.class);

		return errorOrResult(validatedUserOrError(user, pwd), usr -> {
			Cache.removeFromCache(String.format(USER_FMT, userId));
			Cache.removeFromList(USERS_LIST, usr);

			// Delete user shorts and related info asynchronously in a separate thread
			Executors.defaultThreadFactory().newThread(() -> {
				JavaShorts.getInstance().deleteAllShorts(userId, pwd, Token.get(userId));
				JavaBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));
			}).start();

			return DB.deleteOne(usr);
		});
	}

	@Override
	public Result<List<User>> searchUsers(String pattern) {
		Log.info(() -> format("searchUsers : patterns = %s\n", pattern));

		// Access cache
		Result<List<User>> cacheHits = Cache.getList(USERS_LIST, User.class);
		if(Cache.isListCached(USERS_LIST)) {
			List<User> l = cacheHits.value().stream().filter(u -> u.getUserId().contains(pattern))
					.map(User::copyWithoutPassword)
					.toList();
			return ok(l);
		}

		Log.info("List not in cache");
		// If not in cache, access DB
		var query = format("SELECT * FROM users WHERE UPPER(userid) LIKE '%%%s%%'", pattern.toUpperCase());
		List<User> dbHits;
		dbHits = DB.sql(query, User.class)
				.stream()
				.map(User::copyWithoutPassword)
				.toList();

		return ok(dbHits);
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
}
