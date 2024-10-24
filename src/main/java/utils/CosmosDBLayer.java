package utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;

import tukano.api.Result;
import tukano.api.Result.ErrorCode;
import tukano.api.Short;
import tukano.api.User;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;

public class CosmosDBLayer {
    private static final String CONNECTION_URL = "https://cosmos70373.documents.azure.com:443/"; // replace with your own
    private static final String DB_KEY = "ub3hRRVeMSFDD1zsIfSUfa77xfUqMFGYiG13iLv00wUDgFXWJhQlXFbFBj09TfCP76YzbNOGY3iPACDbogECXQ==";
    private static final String DB_NAME = "cosmosdb70373";
    private static final String CONTAINER = "users";

    private static CosmosDBLayer instance;

    public static synchronized CosmosDBLayer getInstance() {
        if (instance != null)
            return instance;

        System.out.println(Props.get("COSMOSDB_URL", ""));

        CosmosClient client = new CosmosClientBuilder()
                //.endpoint(Props.get("COSMOSDB_URL", ""))
                //.key(Props.get("COSMOSDB_KEY", ""))
                .endpoint(CONNECTION_URL)
                .key(DB_KEY)
                // .directMode()
                .gatewayMode()
                // replace by .directMode() for better performance
                .consistencyLevel(ConsistencyLevel.SESSION)
                .connectionSharingAcrossClientsEnabled(true)
                .contentResponseOnWriteEnabled(true)
                .buildClient();
        instance = new CosmosDBLayer(client);
        return instance;
    }

    private final CosmosClient client;
    private CosmosDatabase db;
    private CosmosContainer container;

    public CosmosDBLayer(CosmosClient client) {
        this.client = client;
    }

    private synchronized void init() {
        if (db != null)
            return;
        db = client.getDatabase(DB_NAME);
        //container = db.getContainer(Props.get("COSMOSDB_DATABASE", ""));
        container = db.getContainer(CONTAINER);
    }

    public void close() {
        client.close();
    }

    public <T> Result<T> getOne(String id, Class<T> clazz) {
        return tryCatch(() -> container.readItem(id, new PartitionKey(id), clazz).getItem());
    }

    public <T> Result<?> deleteOne(T obj) {
        return tryCatch(() -> container.deleteItem(obj, new CosmosItemRequestOptions()).getItem());
    }

    public <T> Result<T> updateOne(T obj) {
        return tryCatch(() -> container.upsertItem(obj).getItem());
    }

    public <T> Result<T> insertOne(T obj) {
        return tryCatch(() -> container.createItem(obj).getItem());
    }

    public <T> Result<List<T>> query(Class<T> clazz, String queryStr) {
        return tryCatch(() -> {
            var res = container.queryItems(queryStr, new CosmosQueryRequestOptions(), clazz);
            return res.stream().toList();
        });
    }

    public <T> Result<T> execute(Consumer<CosmosContainer> proc) {
        return execute((cosmosContainer) -> {
            proc.accept(cosmosContainer);
            return Result.ok();
        });
    }

    public <T> Result<T> execute(Function<CosmosContainer, Result<T>> func) {
        try {
            return func.apply(container);
        } catch (CosmosException ex) {
            return Result.error(ErrorCode.CONFLICT);
        } /*catch (Exception e) {
			e.printStackTrace();
			throw e;
		}*/
    }

    <T> Result<T> tryCatch(Supplier<T> supplierFunc) {
        try {
            init();
            return Result.ok(supplierFunc.get());
        } catch (CosmosException ce) {
            System.err.println(ce.getMessage());
            return Result.error(errorCodeFromStatus(ce.getStatusCode()));
        } /*catch (Exception x) {
			x.printStackTrace();
			return Result.error(ErrorCode.INTERNAL_ERROR);
		}*/
    }

    static Result.ErrorCode errorCodeFromStatus(int status) {
        return switch (status) {
            case 200 -> ErrorCode.OK;
            case 404 -> ErrorCode.NOT_FOUND;
            case 409 -> ErrorCode.CONFLICT;
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }
}
