package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.ErrorCode.*;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.ok;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import tukano.api.Result;
import tukano.api.User;
import tukano.api.Users;
import utils.DB;
import utils.PostgreSQL.CosmosPostgresDB;

public class JavaUsers implements Users {

	private static final Logger Log = Logger.getLogger(JavaUsers.class.getName());

	private static Users instance;
	private boolean nosql = false;
	private static CosmosPostgresDB<Object> postgre = CosmosPostgresDB.getInstance();

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

		Cache.insertIntoCache("user", user.getUserId(), user);//TODO: having conflict in database (same id)

		if(nosql)
			return errorOrValue(DB.insertOne(user), user.getUserId());
		else
			return errorOrValue(postgre.insertOne(user), user.getUserId());
	}

	@Override
	public Result<User> getUser(String userId, String pwd) {
		Log.info(() -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null)
			return error(BAD_REQUEST);

		Result<User> user = Cache.getFromCache("user", userId, User.class);
		if (!user.isOK()) {
			if(nosql)
				user = DB.getOne(userId, User.class);
			else
				user = postgre.getOne(userId, User.class);

			if(user.isOK()) Cache.insertIntoCache("user",
					user.value().getUserId(), user.value());
		}
		return validatedUserOrError(user, pwd);
	}

	@Override
	public Result<User> updateUser(String userId, String pwd, User other) {
		Log.info(() -> format("updateUser : userId = %s, pwd = %s, user: %s\n", userId, pwd, other));

		if (badUpdateUserInfo(userId, pwd, other))
			return error(BAD_REQUEST);

		Result<User> user = Cache.getFromCache("user", userId, User.class);
		if(!user.isOK()) {
			if(nosql)
				user = DB.getOne(userId, User.class);
			else
				user = postgre.getOne(userId, User.class);
		}
		return errorOrResult(validatedUserOrError(user, pwd),
				usr -> {
					if(!Cache.insertIntoCache("user", userId, other).isOK())
						return error(BAD_REQUEST);
					if(nosql)
						return DB.updateOne(other);
					else
                        return postgre.updateOne(other);
				});
	}


	@Override
	public Result<User> deleteUser(String userId, String pwd) {
		Log.info(() -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null || pwd == null)
			return error(BAD_REQUEST);

		Result<User> user = Cache.getFromCache("user", userId, User.class);
		if (!user.isOK()) {
			if(nosql)
				user = DB.getOne(userId, User.class);
			else
				user = postgre.getOne(userId, User.class);
		}

		return errorOrResult(validatedUserOrError(user, pwd), usr -> {

			if(!Cache.removeFromCache("user", userId).isOK())
				return error(BAD_REQUEST);

			// Delete user shorts and related info asynchronously in a separate thread
			Executors.defaultThreadFactory().newThread(() -> {
				JavaShorts.getInstance().deleteAllShorts(userId, pwd, Token.get(userId));
				JavaBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));
			}).start();

			if(nosql)
				return (Result<User>) DB.deleteOne(usr);
			else
				return postgre.deleteOne(usr);
		});
	}

	@Override
	public Result<List<User>> searchUsers(String pattern) {
		Log.info(() -> format("searchUsers : patterns = %s\n", pattern));

		var query = format("SELECT * FROM User u WHERE UPPER(u.id) LIKE '%%%s%%'", pattern.toUpperCase());
		var sqlQuery = "SELECT * FROM public.users WHERE UPPER(id) LIKE '%' || UPPER(?) || '%';";
		List<User> hits;
		if(nosql) {
			hits = DB.sql(query, User.class)
					.stream()
					.map(User::copyWithoutPassword)
					.toList();
		}
		else{
            try {
                hits = postgre.queryByPattern( sqlQuery, pattern)
						.stream()
						.map(User::copyWithoutPassword)
						.toList();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
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
}
