package tukano.impl.rest;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import auth.Authentication;
import jakarta.ws.rs.core.Application;

import serverless.IncrementShortViewsResource;
import serverless.TukanoRecommendsResource;
import tukano.api.User;
import tukano.impl.JavaUsers;
import utils.IP;

public class TukanoRestServer extends Application {
	final private static Logger Log = Logger.getLogger(TukanoRestServer.class.getName());

	static String SERVER_BASE_URI = "http://%s:%s/rest";

	private final Set<Object> singletons = new HashSet<>();
	private final Set<Class<?>> resources = new HashSet<>();

	public static final int PORT = 8080;

	public static String serverURI;
			
	static {
		System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s");
	}

	public TukanoRestServer() {
		resources.add(RestUsersResource.class);
		resources.add(RestShortsResource.class);
		resources.add(Authentication.class);
		resources.add(IncrementShortViewsResource.class);
		resources.add(TukanoRecommendsResource.class);
		serverURI = String.format(SERVER_BASE_URI, IP.hostAddress(), PORT);

		User user = new User("Tukano", "12345", "tukano@tukano.com", "Tukano Recommends");
		User admin = new User("admin", "admin", "admin@tukano.com", "Admin");
		if(!JavaUsers.getInstance().getUser(user.getUserId(),user.getPwd()).isOK())
			JavaUsers.getInstance().createUser(user);
		if(!JavaUsers.getInstance().getUser(admin.getUserId(),admin.getPwd()).isOK())
			JavaUsers.getInstance().createUser(admin);
	}

	@Override
	public Set<Class<?>> getClasses() {
		return resources;
	}

	@Override
	public Set<Object> getSingletons() {
		return singletons;
	}

	public static void main(String[] args) throws Exception {
		return;
	}
}
