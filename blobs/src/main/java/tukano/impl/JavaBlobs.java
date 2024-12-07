package tukano.impl;

import static auth.Authentication.ADMIN;
import static java.lang.String.format;
import static utils.Result.ErrorCode.BAD_REQUEST;
import static utils.Result.error;
import static utils.Result.ErrorCode.FORBIDDEN;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Logger;

import auth.Authentication;
import jakarta.ws.rs.core.Cookie;
import tukano.api.Blobs;
import tukano.impl.storage.BlobStorage;
import tukano.impl.storage.FilesystemStorage;
import utils.Result;
import tukano.impl.rest.TukanoRestServer;
import utils.Hash;
import utils.Hex;
import utils.Token;

public class JavaBlobs implements Blobs {

	private static Blobs instance;
	private static final Logger Log = Logger.getLogger(JavaBlobs.class.getName());

	public String baseURI;
	private final BlobStorage storage;

	synchronized public static Blobs getInstance() {
		if( instance == null )
			instance = new JavaBlobs();
		return instance;
	}

	private JavaBlobs() {
		storage = new FilesystemStorage();
		baseURI = String.format("%s/%s/", TukanoRestServer.serverURI, Blobs.NAME);
	}

	@Override
	public Result<Void> upload(String blobId, byte[] bytes, String token, Cookie cookie) {
		Log.info(() -> format("upload : blobId = %s, sha256 = %s, token = %s\n", blobId, Hex.of(Hash.sha256(bytes)), token));

		if (!validBlobId(blobId, token))
			return error(FORBIDDEN);

		String username = blobId.split("\\+")[0];

		Authentication.validateSession(cookie, username);

		return storage.write( toPath( blobId ), bytes);
	}

	@Override
	public Result<byte[]> download(String blobId, String token, Cookie cookie) {
		Log.info(() -> format("download : blobId = %s, token=%s\n", blobId, token));

		if( ! validBlobId( blobId, token ) )
			return error(FORBIDDEN);

		Authentication.validateCookie(cookie);

		try {
			incrementViews(blobId);
		} catch(Exception e) {
			Log.severe("Error incrementing view count for short: " + blobId);
		}

		return storage.read( toPath( blobId ) );
	}

	@Override
	public Result<Void> delete(String blobId, String token, Cookie cookie) {
		Log.info(() -> format("delete : blobId = %s, token=%s\n", blobId, token));

		if( ! validBlobId( blobId, token ) )
			return error(FORBIDDEN);

		Authentication.validateSession(cookie, ADMIN);

		return storage.delete( toPath(blobId));
	}

	@Override
	public Result<Void> deleteAllBlobs(String userId, String token, Cookie cookie) {
		Log.info(() -> format("deleteAllBlobs : userId = %s, token=%s\n", userId, token));

		if( ! utils.Token.isValid( token, userId ) )
			return error(FORBIDDEN);

		Authentication.validateSession(cookie, ADMIN);

		return storage.delete( toPath(userId));
	}

	private boolean validBlobId(String blobId, String token) {
		return Token.isValid(token, blobId);
	}

	private String toPath(String blobId) {
		return blobId.replace("+", "/");
	}

	private Result<Void> incrementViews(String shortId) throws IOException, InterruptedException {
		HttpClient client = HttpClient.newHttpClient();

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://tukano-users-shorts-service:8080/rest/views/" + shortId))
				.POST(null)
				.build();

		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
		if(response.statusCode() != 200)
			return error(BAD_REQUEST);
		return Result.ok();
	}
}