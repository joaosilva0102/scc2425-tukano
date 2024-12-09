package tukano.impl.rest;

import java.util.List;

import jakarta.inject.Singleton;
import jakarta.ws.rs.core.Cookie;
import tukano.api.User;
import tukano.api.Users;
import tukano.api.rest.RestUsers;
import tukano.impl.JavaUsers;
import tukano.impl.JavaUsersNoCache;
import utils.RestResource;

@Singleton
public class RestUsersResource extends RestResource implements RestUsers {

	private static boolean cache = true;

	final Users impl;
	public RestUsersResource() {
		this.impl = cache? JavaUsers.getInstance() : JavaUsersNoCache.getInstance();
	}
	
	@Override
	public String createUser(User user) {
		return super.resultOrThrow( impl.createUser( user));
	}

	@Override
	public User getUser(String name, String pwd) {
		return super.resultOrThrow( impl.getUser(name, pwd));
	}
	
	@Override
	public User updateUser(String name, String pwd, User user) {
		return super.resultOrThrow( impl.updateUser(name, pwd, user));
	}

	@Override
	public User deleteUser(String name, String pwd, Cookie cookie) {
		return super.resultOrThrow( impl.deleteUser(name, pwd, cookie));
	}

	@Override
	public List<User> searchUsers(String pattern) {
		return super.resultOrThrow( impl.searchUsers(pattern));
	}
}
