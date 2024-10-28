package tukano.impl;

import tukano.api.Result;
import utils.JSON;
import utils.RedisCache;

import java.util.logging.Logger;

import static tukano.api.Result.ErrorCode.NOT_FOUND;
import static tukano.api.Result.error;
import static tukano.api.Result.ok;

public class Cache {

    private static final Logger Log = Logger.getLogger(Cache.class.getName());

    protected static <T> Result<T> getFromCache(String keyType, String keyValue, Class<T> clazz) {
        try (var jedis = RedisCache.getCachePool().getResource()) {
            var key = String.format("%s:%s",keyType, keyValue);
            var value = jedis.get(key);
            if(value != null) {
                Log.info("Retrieved data from cache");
                return ok(JSON.decode(value, clazz));
            } else
                return error(NOT_FOUND);
        }
    }

    protected static <T> Result<T> insertIntoCache(String keyType, String keyValue, T obj) {
        try (var jedis = RedisCache.getCachePool().getResource()) {
            var key = String.format("%s:%s",keyType, keyValue);
            var value = JSON.encode(obj);
            jedis.set(key, value);
            Log.info("Inserted data into cache");
            return ok();
        }
    }

    protected static Result<Void> removeFromCache(String keyType, String keyValue) {
        try (var jedis = RedisCache.getCachePool().getResource()) {
            var key = String.format("%s:%s",keyType, keyValue);
            jedis.del(key);
            Log.info("Removed data from cache");
            return ok();
        }
    }
}
