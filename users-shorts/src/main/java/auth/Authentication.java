package auth;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import tukano.impl.JavaUsers;
import utils.cache.Cache;

import java.net.URI;
import java.util.UUID;
import java.util.logging.Logger;

@Path(Authentication.PATH)
public class Authentication {
	static final String PATH = "login";
	static final String USER = "username";
	static final String PWD = "password";
	public static final String COOKIE_KEY = "scc:session";
	static final String LOGIN_PAGE = "login.html";
	private static final int MAX_COOKIE_AGE = 3600;
	static final String REDIRECT_TO_AFTER_LOGIN = "/";
	private static final Logger Log = Logger.getLogger(Authentication.class.getName());

	@POST
	public Response login( @FormParam(USER) String user, @FormParam(PWD) String password ) {
		Log.info("user: " + user + " pwd:" + password );
		boolean pwdOk = JavaUsers.getInstance().getUser(user, password).isOK();
		if (pwdOk) {
			String uid = UUID.randomUUID().toString();
			var cookie = new NewCookie.Builder(COOKIE_KEY)
					.value(uid).path("/")
					.comment("sessionId")
					.maxAge(MAX_COOKIE_AGE)
					.secure(false) //ideally it should be true to only work for https requests
					.httpOnly(true)
					.build();

			Cache.insertIntoCache(uid, new Session(uid, user));
			
            return Response.seeOther(URI.create( "/users/" + user + "?pwd=" + password ))
                    .cookie(cookie) 
                    .build();
		} else
			throw new NotAuthorizedException("Incorrect login");
	}

	@GET
	@Produces(MediaType.TEXT_HTML)
	public String login() {
		try {
			var in = getClass().getClassLoader().getResourceAsStream(LOGIN_PAGE);
			return new String( in.readAllBytes() );			
		} catch( Exception x ) {
			throw new WebApplicationException( Response.Status.INTERNAL_SERVER_ERROR );
		}
	}
}
