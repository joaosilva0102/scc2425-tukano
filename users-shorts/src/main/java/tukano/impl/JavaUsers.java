package tukano.impl;

import static auth.Authentication.COOKIE_KEY;
import static java.lang.String.format;
import static utils.Result.ErrorCode.*;
import static utils.Result.error;
import static utils.Result.errorOrResult;
import static utils.Result.errorOrValue;
import static utils.Result.ok;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.NewCookie;
import utils.Result;
import tukano.api.User;
import tukano.api.Users;
import utils.Token;
import utils.cache.Cache;
import utils.database.DB;

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

	private JavaUsers() {}


	@Override
	public Result<String> createUser(User user) {
		Log.info(() -> format("createUser : %s\n", user));

		if (badUserInfo(user))
			return error(BAD_REQUEST);

		String userCache = String.format(USER_FMT, user.getUserId());
		if (Cache.isCached(userCache))
			return error(CONFLICT);

		Result<User> r = DB.insertOne(user);

		if(!Cache.insertIntoCache(String.format(USER_FMT, user.getUserId()), user).isOK() ||
				!Cache.appendList(USERS_LIST, user).isOK())
			Log.warning("Error while inserting user into cache");

		if(!user.getUserId().equals("Tukano")) {
			var result = JavaShorts.getInstance().follow(user.getUserId(), "Tukano",
					true, user.getPwd());
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
	public Result<User> deleteUser(String userId, String pwd, Cookie cookie) {
		Log.info(() -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null || pwd == null)
			return error(BAD_REQUEST);

		Result<User> user = Cache.getFromCache(String.format(USER_FMT, userId), User.class);
		if (!user.isOK())
				user = DB.getOne(userId, User.class);

		return errorOrResult(validatedUserOrError(user, pwd),
				(validatedUser) -> removeUserFromSystem(validatedUser, cookie));
	}

	@Override
	public Result<List<User>> searchUsers(String pattern) {
		Log.info(() -> format("searchUsers : patterns = %s\n", pattern));

		// Access cache
		Result<List<User>> cacheHits = Cache.getList(USERS_LIST, User.class);
		if(Cache.isListCached(USERS_LIST) && !cacheHits.value().isEmpty()) {
			List<User> cachedUserList = cacheHits.value().stream().filter(u -> u.getUserId().contains(pattern))
					.map(User::copyWithoutPassword)
					.toList();
			return ok(cachedUserList);
		}

		// If not in cache, access DB
		var query = format("SELECT * FROM users u WHERE UPPER(u.userid) LIKE '%%%s%%'", pattern.toUpperCase());
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

	private Result<User> removeUserFromSystem(User user, Cookie cookie) {
		String userId = user.getUserId();
		Cache.removeFromCache(String.format(USER_FMT, userId));
		Cache.removeFromList(USERS_LIST, user);

		// Delete user shorts and related info asynchronously in a separate thread
		Executors.defaultThreadFactory().newThread(() -> {
			JavaShorts.getInstance().deleteAllShorts(userId, user.getPwd(), Token.get(userId));
            try {
                deleteAllBlobs(userId, cookie);
            } catch (Exception e) {
                Log.warning("User not authorized to delete the blob");
            }
        }).start();

		return DB.deleteOne(user);
	}

	private Result<Void> deleteAllBlobs(String userId, Cookie cookie) throws IOException, InterruptedException {
		HttpClient client = HttpClient.newHttpClient();

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://tukano-blobs-service:8080/rest/blobs/" + userId + "/blobs?token=" + Token.get(userId)))
				.header("Cookie", COOKIE_KEY + "=" + cookie.getValue())
				.DELETE()
				.build();

		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
		if(response.statusCode() != 200)
			return error(BAD_REQUEST);
		return Result.ok();
	}
}
