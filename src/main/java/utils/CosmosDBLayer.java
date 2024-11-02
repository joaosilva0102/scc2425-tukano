package utils;

import com.azure.cosmos.*;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import tukano.api.Result;
import tukano.api.Result.ErrorCode;
import tukano.api.Short;
import tukano.api.User;
import tukano.impl.JavaUsers;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class CosmosDBLayer {
    private static final Map<Class<?>, String> containerMap = new HashMap<>();
    private static final Logger Log = Logger.getLogger(JavaUsers.class.getName());

    static {
        containerMap.put(User.class, "users");
        containerMap.put(Short.class, "shorts");
        containerMap.put(Following.class, "followers");
        containerMap.put(Likes.class, "likes");
    }

    private static CosmosDBLayer instance;

    public static synchronized CosmosDBLayer getInstance() {
        Log.info("Accessing CosmosDB...");
        if (instance != null)
            return instance;

        CosmosClient client = new CosmosClientBuilder()
                .endpoint(Props.get("COSMOSDB_URL", ""))
                .key(Props.get("COSMOSDB_KEY", ""))
                //.directMode()
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
    private final Map<String, CosmosContainer> containers = new HashMap<>();

    public CosmosDBLayer(CosmosClient client) {
        this.client = client;
    }

    private synchronized void init() {
        if (db != null)
            return;
        db = client.getDatabase(Props.get("COSMOSDB_DATABASE", ""));
        for (String containerName : containerMap.values()) {
            containers.put(containerName, db.getContainer(containerName));
        }
    }

    private CosmosContainer getContainer(Class<?> clazz) {
        init();
        String containerName = containerMap.get(clazz);
        if (containerName == null) {
            throw new IllegalArgumentException("No container found for class: " + clazz.getName());
        }
        return containers.get(containerName);
    }

    public void close() {
        client.close();
    }

    public <T> Result<T> getOne(String id, Class<T> clazz) {
        return tryCatch(() -> getContainer(clazz).readItem(id, new PartitionKey(id), clazz).getItem());
    }

    public <T> Result<?> deleteOne(T obj) {
        return tryCatch(() -> getContainer(obj.getClass()).deleteItem(obj, new CosmosItemRequestOptions()).getItem());
    }

    public <T> Result<T> updateOne(T obj) {
        return tryCatch(() -> getContainer(obj.getClass()).upsertItem(obj).getItem());
    }

    public <T> Result<T> insertOne(T obj) {
        return tryCatch(() -> getContainer(obj.getClass()).createItem(obj).getItem());
    }

    public <T> Result<List<T>> query(Class<T> clazz, String queryStr) {
        return tryCatch(() -> {
            var res = getContainer(clazz).queryItems(queryStr, new CosmosQueryRequestOptions(), clazz);
            return res.stream().toList();
        });
    }

    public <T> Result<T> execute(Consumer<CosmosContainer> proc, Class<T> clazz) {
        return execute((cosmosContainer) -> {
            proc.accept(cosmosContainer);
            return Result.ok();
        }, clazz);
    }

    public <T> Result<T> execute(Function<CosmosContainer, Result<T>> func, Class<T> clazz) {
        try {
            return func.apply(getContainer(clazz));
        } catch (CosmosException ex) {
            return Result.error(ErrorCode.CONFLICT);
        } /*catch (Exception e) {
			e.printStackTrace();
			throw e;
		}*/
    }

    public Result<Void> clearAllContainers() {
        for (CosmosContainerProperties containerProperties : db.readAllContainers().stream().toList()) {
            CosmosContainer container = db.getContainer(containerProperties.getId());
            Log.info("Deleting documents from container: " + containerProperties.getId());

            // Delete all items in the container
            container.queryItems("SELECT * FROM c", new CosmosQueryRequestOptions(), Object.class)
                    .forEach(item -> container.deleteItem(item, new CosmosItemRequestOptions()));
        }
        return Result.ok();
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

    static ErrorCode errorCodeFromStatus(int status) {
        return switch (status) {
            case 200 -> ErrorCode.OK;
            case 404 -> ErrorCode.NOT_FOUND;
            case 409 -> ErrorCode.CONFLICT;
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }
}
