package tukano.impl.storage;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import tukano.api.Result;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Consumer;

import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.INTERNAL_ERROR;
import static tukano.api.Result.error;
import static tukano.api.Result.ok;

public class CloudBlobStorage implements BlobStorage {

    private final String storageConnectionString = " ";
    private static final String BLOBS_CONTAINER_NAME = " ";

    public CloudBlobStorage() {}

    @Override
    public Result<Void> write(String path, byte[] bytes) {
        try {
            BlobContainerClient containerClient = new BlobContainerClientBuilder()
                    .connectionString(storageConnectionString)
                    .containerName(BLOBS_CONTAINER_NAME)
                    .buildClient();

            BlobClient blob = containerClient.getBlobClient(path);
            blob.upload(BinaryData.fromBytes(bytes));
            System.out.println("File uploaded : " + path);
        }
        catch (Exception e) {
            e.printStackTrace();
           return error(BAD_REQUEST);
        }
        return ok();
    }

    @Override
    public Result<Void> delete(String path) {
        if (path == null)
            return error(BAD_REQUEST);

        try {
            BlobContainerClient containerClient = new BlobContainerClientBuilder()
                    .connectionString(storageConnectionString)
                    .containerName(BLOBS_CONTAINER_NAME)
                    .buildClient();

            BlobClient blob = containerClient.getBlobClient(path);
            blob.delete();
        } catch (Exception e) {
            e.printStackTrace();
            return error(INTERNAL_ERROR);
        }
        return ok();
    }

    @Override
    public Result<byte[]> read(String path) {
        BinaryData bytes;
        try {
            BlobContainerClient containerClient = new BlobContainerClientBuilder()
                    .connectionString(storageConnectionString)
                    .containerName(BLOBS_CONTAINER_NAME)
                    .buildClient();

            BlobClient blob = containerClient.getBlobClient(path);
             bytes = blob.downloadContent();
            System.out.println("File uploaded : " + path);
        }
        catch (Exception e) {
            e.printStackTrace();
            return error(BAD_REQUEST);
        }
        return bytes != null ? ok( bytes.toBytes() ) : error( INTERNAL_ERROR );
    }

    @Override
    public Result<Void> read(String path, Consumer<byte[]> sink) {
        return null;
    }
}
