package tukano.impl.storage;

import com.azure.core.http.rest.PagedIterable;
import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlobRange;
import com.azure.storage.blob.models.DownloadRetryOptions;
import tukano.api.Result;
import utils.Hash;
import utils.Props;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
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
                .connectionString(System.getProperty("BlobStoreConnection"))
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
    public Result<byte[]> read(String path) {
        BinaryData bytes = null;

        if (path == null)
            return error(BAD_REQUEST);
        try {
            BlobClient blob = containerClient.getBlobClient(path);

            if (!blob.exists())
                return error(NOT_FOUND);

            bytes = blob.downloadContent();
            System.out.println("File downloaded : " + path);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bytes != null ? ok(bytes.toBytes()) : error(INTERNAL_ERROR);
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
    public Result<Void> read(String path, Consumer<byte[]> sink) {
        if (path == null) {
            return error(BAD_REQUEST);
        }

        try {
            BlobClient blob = containerClient.getBlobClient(path);
            if (!blob.exists()) {
                return error(NOT_FOUND);
            }
            try (InputStream inputStream = blob.openInputStream()) {
                byte[] buffer = new byte[CHUNK_SIZE];
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) { //denotes end of stream
                    if (bytesRead < CHUNK_SIZE) {
                        byte[] bytes = Arrays.copyOf(buffer, bytesRead);
                        sink.accept(bytes);
                    } else {
                        sink.accept(buffer);
                    }
                }
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
            PagedIterable<BlobItem> allBlobs = containerClient.listBlobsByHierarchy(path + "/");

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

