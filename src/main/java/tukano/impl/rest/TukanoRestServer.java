package tukano.impl.rest;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import jakarta.ws.rs.core.Application;

import tukano.api.User;
import tukano.impl.JavaUsers;
import utils.IP;
import utils.Props;

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
		resources.add(RestBlobsResource.class);
		resources.add(RestUsersResource.class);
		resources.add(RestShortsResource.class);
		serverURI = String.format(SERVER_BASE_URI, IP.hostAddress(), PORT);
		Props.load("azurekeys-region.props");
		//Props.load("azurekeys-northeurope.props");
		User user = new User("Tukano", "12345", "tukano@tukano.com", "Tukano Recommends");
		if(!JavaUsers.getInstance().getUser(user.getUserId(),user.getPwd()).isOK())
			JavaUsers.getInstance().createUser(user);
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
