package tukano.api;

import jakarta.ws.rs.core.Cookie;
import utils.Result;

/**
 * Interface of blob service for storing short videos media ...
 */
public interface Blobs {
	String NAME = "blobs";

	/**
	 * Uploads a short video blob resource. Must validate the blobId to ensure it
	 * was generated by the Shorts service.
	 *
	 * @param blobId blobId the identifier generated by the Shorts service for this
	 *               blob
	 * @param bytes  the contents in bytes of the blob resource
	 *
	 * @return OK(void) if the upload is new or if the blobId and bytes match an
	 *         existing blob;
	 *         CONFLICT if a blobId exists but bytes do not match;
	 *         FORBIDDEN if the blobId is not valid
	 */
	utils.Result<Void> upload(String blobId, byte[] bytes, String token, Cookie cookie);

	/**
	 * Downloads a short video blob resource in a single byte chunk of bytes.
	 *
	 * @param blobId the id of the blob;
	 * @return (OK, bytes), if the blob exists;
	 * 			 NOT_FOUND, if no blob matches the provided blobId
	 */
	utils.Result<byte[]> download(String blobId, String token, Cookie cookie);


	/**
	 * Deletes a short video blob resource.
	 *
	 * @param blobId the id of the blob;
	 * @return (OK, void), if the blob exists and was deleted;
	 * 			 NOT_FOUND, if no blob matches the provided blobId
	 */
	utils.Result<Void> delete(String blobId, String token, Cookie cookie );


	/**
	 * Deletes all short video blob resources from a given userId.
	 *
	 * @param userId the id of the owner of the blobs;
	 */
	Result<Void> deleteAllBlobs(String userId, String token, Cookie cookie );
}