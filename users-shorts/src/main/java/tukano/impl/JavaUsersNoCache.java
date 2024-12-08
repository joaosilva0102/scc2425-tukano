package tukano.impl;

import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.NewCookie;
import org.checkerframework.checker.units.qual.C;
import utils.Result;
import tukano.api.User;
import tukano.api.Users;
import utils.Token;
import utils.database.DB;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import static auth.Authentication.COOKIE_KEY;
import static java.lang.String.format;
import static utils.Result.ErrorCode.*;
import static utils.Result.*;

public class JavaUsersNoCache implements Users {

	private static final Logger Log = Logger.getLogger(JavaUsersNoCache.class.getName());
	private static Users instance;

	synchronized public static Users getInstance() {
		if (instance == null)
			instance = new JavaUsersNoCache();
		return instance;
	}

	private JavaUsersNoCache() {}

	@Override
	public Result<String> createUser(User user) {
		Log.info(() -> format("createUser : %s\n", user));

		if( badUserInfo( user ) )
			return error(BAD_REQUEST);

		return errorOrValue( DB.insertOne( user), user.getUserId() );
	}

	@Override
	public Result<User> getUser(String userId, String pwd) {
		Log.info( () -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null)
			return error(BAD_REQUEST);

		return validatedUserOrError( DB.getOne( userId, User.class), pwd);
	}

	@Override
	public Result<User> updateUser(String userId, String pwd, User other) {
		Log.info(() -> format("updateUser : userId = %s, pwd = %s, user: %s\n", userId, pwd, other));

		if (badUpdateUserInfo(userId, pwd, other))
			return error(BAD_REQUEST);

		return errorOrResult( validatedUserOrError(DB.getOne( userId, User.class), pwd),
				user -> DB.updateOne( user.updateFrom(other)));
	}

	@Override
	public Result<User> deleteUser(String userId, String pwd, Cookie cookie) {
		Log.info(() -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null || pwd == null )
			return error(BAD_REQUEST);

		return errorOrResult( validatedUserOrError(DB.getOne( userId, User.class), pwd), user -> {

			// Delete user shorts and related info asynchronously in a separate thread
			Executors.defaultThreadFactory().newThread( () -> {
				JavaShortsNoCache.getInstance().deleteAllShorts(userId, pwd, utils.Token.get(userId));
				try {
					deleteAllBlobs(userId, cookie);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}).start();

			return DB.deleteOne( user);
		});
	}

	@Override
	public Result<List<User>> searchUsers(String pattern) {
		Log.info( () -> format("searchUsers : patterns = %s\n", pattern));

		var query = format("SELECT * FROM User u WHERE UPPER(u.userid) LIKE '%%%s%%'", pattern.toUpperCase());
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
