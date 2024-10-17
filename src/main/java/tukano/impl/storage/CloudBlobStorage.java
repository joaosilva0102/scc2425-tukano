package tukano.impl.storage;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import tukano.api.Result;
import utils.Hash;
import utils.IO;

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

    private final String storageConnectionString = "DefaultEndpointsProtocol=https;AccountName=sto62612northeurope;AccountKey=pYe+UhUDzJXoDsIzBG0SbAt4ic5yGQOwqQpFH0gjlpTDiD6RxiR6+VDUXXMyvEPHWBLJXwQKKAZy+AStoYUDiQ==;EndpointSuffix=core.windows.net";
    private static final String BLOBS_CONTAINER_NAME = "shorts";
    private static BlobContainerClient containerClient;

    public CloudBlobStorage() {
        containerClient = new BlobContainerClientBuilder()
                .connectionString(storageConnectionString)
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

            if (blob.exists())
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
        return null;
    }
}
