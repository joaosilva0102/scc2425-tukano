package tukano.impl.rest;

import jakarta.inject.Singleton;
import jakarta.ws.rs.core.Cookie;
import tukano.api.Blobs;
import tukano.api.rest.RestBlobs;
import tukano.impl.JavaBlobs;
import utils.RestResource;

@Singleton
public class RestBlobsResource extends RestResource implements RestBlobs {

	final Blobs impl;
	
	public RestBlobsResource() {
		this.impl = JavaBlobs.getInstance();
	}
	
	@Override
	public void upload(String blobId, byte[] bytes, String token, Cookie cookie) {
		super.resultOrThrow( impl.upload(blobId, bytes, token, cookie));
	}

	@Override
	public byte[] download(String blobId, String token, Cookie cookie) {
		return super.resultOrThrow( impl.download( blobId, token, cookie ));
	}

	@Override
	public void delete(String blobId, String token, Cookie cookie) {
		super.resultOrThrow( impl.delete( blobId, token, cookie ));
	}
	
	@Override
	public void deleteAllBlobs(String userId, String password, Cookie cookie) {
		super.resultOrThrow( impl.deleteAllBlobs( userId, password, cookie ));
	}
}
