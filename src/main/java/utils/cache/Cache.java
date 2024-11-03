package utils.cache;

import tukano.api.Result;
import utils.JSON;

import java.util.List;
import java.util.logging.Logger;

import static tukano.api.Result.ErrorCode.NOT_FOUND;
import static tukano.api.Result.error;
import static tukano.api.Result.ok;

public class Cache {

    private static final Logger Log = Logger.getLogger(Cache.class.getName());

    public static <T> Result<T> getFromCache(String key, Class<T> clazz) {
        try (var jedis = RedisCache.getCachePool().getResource()) {
            var value = jedis.get(key);
            if(value != null) {
                Log.info("Retrieved data from cache: " + key);
                return ok(JSON.decode(value, clazz));
            } else
                return error(NOT_FOUND);
        }
    }

    public static <T> Result<T> insertIntoCache(String key, T obj) {
        try (var jedis = RedisCache.getCachePool().getResource()) {
            var value = JSON.encode(obj);
            jedis.set(key, value);
            Log.info("Inserted data into cache: " + key);
            return ok();
        }
    }

    public static Result<Void> removeFromCache(String key) {
        try (var jedis = RedisCache.getCachePool().getResource()) {
            jedis.del(key);
            Log.info("Removed data from cache: " + key);
            return ok();
        }
    }

    public static Result<Void> incrementKey(String key) {
        try (var jedis = RedisCache.getCachePool().getResource()) {
            jedis.incr(key);
            Log.info("Incremented key: " + key);
            return ok();
        }

    }

    public static <T> Result<Void> appendList(String key, T obj) {
        try (var jedis = RedisCache.getCachePool().getResource()) {
            long n = jedis.rpush(key, JSON.encode(obj));
            Log.info(n + "elements appended to list: " + key);
            return ok();
        }
    }

    public static <T> Result<Void> removeFromList(String key, T obj) {
        try (var jedis = RedisCache.getCachePool().getResource()) {
            System.out.println(JSON.encode(obj));
            long n = jedis.lrem(key, 1, JSON.encode(obj));
            Log.info(n + " elements removed from cache list: " + key);
            return ok();
        }
    }

    public static <T> Result<Void> updateList(String key, T prevObj, T newObj) {
        try (var jedis = RedisCache.getCachePool().getResource()) {
            jedis.lset(key, jedis.lpos(key, JSON.encode(prevObj)), JSON.encode(newObj));
            Log.info("Element updated in cache list: " + key);
            return ok();
        }
    }

    public static <T> Result<List<T>> getList(String key, Class<T> clazz) {
        try (var jedis = RedisCache.getCachePool().getResource()) {
            var list = jedis.lrange(key, 0, -1).stream()
                    .map(e -> JSON.decode(e, clazz)).toList();
            Log.info("Getting cache list: " + key);
            return ok(list);
        }
    }

    public static boolean isListCached(String key) {
        try (var jedis = RedisCache.getCachePool().getResource()) {
            Log.info("Checking if list is in cache: " + key);
            return jedis.exists(key);
        }
    }

    public static boolean isCached(String key) {
        try (var jedis = RedisCache.getCachePool().getResource()) {
            Log.info("Checking if key is in cache: " + key);
            return jedis.exists(key);
        }
    }

    public static <T> Result<Void> replaceList(String key, List<T> newList) {
        try (var jedis = RedisCache.getCachePool().getResource()) {
            jedis.del(key);
            if (!newList.isEmpty()) jedis.lpush(key,
                    newList.stream().map(JSON::encode).toArray(String[]::new));
            return ok();
        }
    }

    public static void flushAll() {
        try (var jedis = RedisCache.getCachePool().getResource()) {
            jedis.flushAll();
        }
    }
}
