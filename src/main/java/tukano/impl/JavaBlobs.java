package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.ErrorCode.INTERNAL_ERROR;
import static tukano.api.Result.error;
import static tukano.api.Result.ErrorCode.FORBIDDEN;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Consumer;
import java.util.logging.Logger;

import tukano.api.Blobs;
import tukano.api.Result;
import tukano.impl.rest.TukanoRestServer;
import tukano.impl.storage.BlobStorage;
import tukano.impl.storage.FilesystemStorage;
import tukano.impl.storage.CloudBlobStorage;
import utils.Hash;
import utils.Hex;

public class JavaBlobs implements Blobs {

	private static Blobs instance;
	private static Logger Log = Logger.getLogger(JavaBlobs.class.getName());

	public String baseURI;
	private BlobStorage storage;
	private CloudBlobStorage cloudStorage;

	synchronized public static Blobs getInstance() {
		if( instance == null )
			instance = new JavaBlobs();
		return instance;
	}

	private JavaBlobs() {
		storage = new FilesystemStorage();
//		cloudStorage = new CloudBlobStorage();
		baseURI = String.format("%s/%s/", TukanoRestServer.serverURI, Blobs.NAME);
	}

	@Override
	public Result<Void> upload(String blobId, byte[] bytes, String token) {
		Log.info(() -> format("upload : blobId = %s, sha256 = %s, token = %s\n", blobId, Hex.of(Hash.sha256(bytes)), token));

		if (!validBlobId(blobId, token))
			return error(FORBIDDEN);

		return storage.write( toPath( blobId ), bytes);
//		return cloudStorage.write( toPath( blobId ), bytes);
	}

	@Override
	public Result<byte[]> download(String blobId, String token) {
		Log.info(() -> format("download : blobId = %s, token=%s\n", blobId, token));

		if( ! validBlobId( blobId, token ) )
			return error(FORBIDDEN);

/*		Result<Void> incRes = incrementShortViews(blobId);
		if(!incRes.isOK())
			return Result.error(incRes.error());
*/		return storage.read( toPath( blobId ) );
//		return cloudStorage.read( toPath( blobId ) );
	}
	
	public Result<Void> downloadToSink(String blobId, Consumer<byte[]> sink, String token) {
		Log.info(() -> format("downloadToSink : blobId = %s, token = %s\n", blobId, token));

		if( ! validBlobId( blobId, token ) )
			return error(FORBIDDEN);

		return storage.read( toPath(blobId), sink);
//		return cloudStorage.read( toPath(blobId), sink);
	}

	@Override
	public Result<Void> delete(String blobId, String token) {
		Log.info(() -> format("delete : blobId = %s, token=%s\n", blobId, token));

		if( ! validBlobId( blobId, token ) )
			return error(FORBIDDEN);

		return storage.delete( toPath(blobId));
//		return cloudStorage.delete( toPath(blobId));
	}

	@Override
	public Result<Void> deleteAllBlobs(String userId, String token) {
		Log.info(() -> format("deleteAllBlobs : userId = %s, token=%s\n", userId, token));

		if( ! Token.isValid( token, userId ) )
			return error(FORBIDDEN);

		return storage.delete( toPath(userId));
//		return cloudStorage.deleteAll(userId);
	}

	private boolean validBlobId(String blobId, String token) {
		return Token.isValid(token, blobId);
	}

	private String toPath(String blobId) {
		return blobId.replace("+", "/");
	}
	
	private String toURL( String blobId ) {
		return baseURI + blobId ;
	}

	/**
	 * Calls the Serverless Azure function IncrementShortViews using a HTTP request
	 * @param blobId id of the blob/short to increment view count
	 * @return the result of the method
	 */
	private Result<Void> incrementShortViews(String blobId) {
		try {
			HttpClient client = HttpClient.newHttpClient();
			String url = System.getProperty("FUNCTIONS_INCREMENT_URL") + blobId;
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(url))
					.POST(HttpRequest.BodyPublishers.ofString(""))
					.build();

			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if(response.statusCode() != 200)
				return error(INTERNAL_ERROR);
		} catch (Exception e) {
			Log.warning("Error while incrementing short views");
			return Result.error(INTERNAL_ERROR);
		}

		return Result.ok();
	}
}
