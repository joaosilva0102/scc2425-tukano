package utils.cache;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class RedisCache {
	private static final int REDIS_PORT = 6379;
	private static final int REDIS_TIMEOUT = 3600;
	private static final boolean Redis_USE_TLS = false;
	private static final String REDIS_HOSTNAME = "redis-service";
	
	private static JedisPool instance;
	
	public synchronized static JedisPool getCachePool() {
		if( instance != null)
			return instance;

		var poolConfig = new JedisPoolConfig();
		poolConfig.setMaxTotal(128);
		poolConfig.setMaxIdle(128);
		poolConfig.setMinIdle(16);
		poolConfig.setTestOnBorrow(true);
		poolConfig.setTestOnReturn(true);
		poolConfig.setTestWhileIdle(true);
		poolConfig.setNumTestsPerEvictionRun(3);
		poolConfig.setBlockWhenExhausted(true);
		instance = new JedisPool(poolConfig, REDIS_HOSTNAME,
				REDIS_PORT, REDIS_TIMEOUT, null, Redis_USE_TLS);
		return instance;
	}
}
