package tukano.impl.storage;

import com.azure.core.http.rest.PagedIterable;
import com.azure.core.util.BinaryData;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobRange;
import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.DownloadRetryOptions;
import tukano.api.Result;
import utils.Hash;
import utils.IO;
import utils.Props;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Consumer;

import static tukano.api.Result.ErrorCode.*;
import static tukano.api.Result.error;
import static tukano.api.Result.ok;

public class CloudBlobStorage implements BlobStorage {

    private static final String BLOBS_CONTAINER_NAME = "shorts";
    private static final int CHUNK_SIZE = 4096;
    private static BlobContainerClient containerClient;

    public CloudBlobStorage() {
        containerClient = new BlobContainerClientBuilder()
                .connectionString(Props.get("BlobStoreConnection", ""))
                .containerName(BLOBS_CONTAINER_NAME)
                .buildClient();
    }

    @Override
    public Result<Void> write(String path, byte[] bytes) {
        if (path == null)
            return error(BAD_REQUEST);

        try {
            BlobClient blob = containerClient.getBlobClient(path);
            if (blob.exists()) {
                if (Arrays.equals(Hash.sha256(bytes), Hash.sha256(blob.downloadContent().toBytes())))
                    return ok();
                else
                    return error(CONFLICT);
            }
            blob.upload(BinaryData.fromBytes(bytes));
            System.out.println("File uploaded : " + path);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ok();
    }

    @Override
    public Result<Void> delete(String path) {
        if (path == null)
            return error(BAD_REQUEST);

        try {
            BlobClient blob = containerClient.getBlobClient(path);
            if (!blob.exists())
                return error(NOT_FOUND);

            blob.delete();
        } catch (Exception e) {
            e.printStackTrace();
            return error(INTERNAL_ERROR);
        }
        return ok();
    }

    @Override
    public Result<byte[]> read(String path) {
        BinaryData bytes = null;

        if (path == null)
            return error(BAD_REQUEST);
        try {
            BlobClient blob = containerClient.getBlobClient(path);

            if (!blob.exists())
                return error(NOT_FOUND);

            bytes = blob.downloadContent();
            System.out.println("File uploaded : " + path);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bytes != null ? ok(bytes.toBytes()) : error(INTERNAL_ERROR);
    }

    @Override
    public Result<Void> read(String path, Consumer<byte[]> sink) {
        if (path == null)
            return error(BAD_REQUEST);

        try {
            BlobClient blob = containerClient.getBlobClient(path);
            if (!blob.exists())
                return error(NOT_FOUND);

            long blobSize = blob.getProperties().getBlobSize();

            for (long offset = 0; offset < blobSize; offset += CHUNK_SIZE) {
                long chunkSize = (long) Math.min(CHUNK_SIZE, blobSize - offset);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                /*blob.downloadStreamWithResponse(
                        new ByteArrayOutputStream(),
                        new BlobRange(offset, chunkSize),
                        new DownloadRetryOptions().setMaxRetryRequests(5),
                        null,
                        false,
                        new Context(key2, value2)).getStatusCode()
                );*/
                sink.accept(outputStream.toByteArray());
            }
            return ok();
        } catch (Exception e) {
            return error(INTERNAL_ERROR);
        }
    }

    public Result<Void> deleteAll(String path) {
        if (path == null)
            return error(BAD_REQUEST);

        try {
            PagedIterable<BlobItem> allBlobs = containerClient.listBlobs();

            for (BlobItem blobItem : allBlobs) {
                BlobClient blob = containerClient.getBlobClient(blobItem.getName());
                blob.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return error(INTERNAL_ERROR);
        }
        return ok();
    }


    }

