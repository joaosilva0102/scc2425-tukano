package auth;

import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.Cookie;
import utils.cache.Cache;

import java.util.logging.Logger;

import static java.lang.String.format;

public class Authentication {
    public static final String COOKIE_KEY = "scc:session";
    private static final Logger Log = Logger.getLogger(Authentication.class.getName());

    public static final String ADMIN = "admin";

    static public Session validateCookie(Cookie cookie) throws NotAuthorizedException {
        if (cookie == null)
            throw new NotAuthorizedException("No session initialized");

        var sessionCache = Cache.getFromCache(cookie.getValue(), Session.class);
        if (sessionCache == null || !sessionCache.isOK())
            throw new NotAuthorizedException("No valid session initialized");

        var session = sessionCache.value();
        if (session.user() == null || session.user().isEmpty())
            throw new NotAuthorizedException("No valid session initialized");

        Log.info(() -> format("Session user: %s", session.user()));

        return session;
    }

    static public Session validateSession(Cookie cookie, String userId) throws NotAuthorizedException {
        var session = validateCookie(cookie);

        if (!session.user().equals(userId) && !session.user().equals(ADMIN))
            throw new NotAuthorizedException("Invalid user : " + session.user());

        return session;
    }
}