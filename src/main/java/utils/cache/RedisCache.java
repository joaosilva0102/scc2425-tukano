package utils.cache;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import utils.Props;

public class RedisCache {
	private static final int REDIS_PORT = 6379;
	private static final int REDIS_TIMEOUT = 1000;
	private static final boolean Redis_USE_TLS = false;
	
	private static JedisPool instance;
	
	public synchronized static JedisPool getCachePool() {
		if( instance != null)
			return instance;

		//var hostname = System.getProperty("REDIS_HOSTNAME");
		var hostname = "redis-service";
		var poolConfig = new JedisPoolConfig();
		poolConfig.setMaxTotal(128);
		poolConfig.setMaxIdle(128);
		poolConfig.setMinIdle(16);
		poolConfig.setTestOnBorrow(true);
		poolConfig.setTestOnReturn(true);
		poolConfig.setTestWhileIdle(true);
		poolConfig.setNumTestsPerEvictionRun(3);
		poolConfig.setBlockWhenExhausted(true);
		instance = new JedisPool(poolConfig, hostname,
				REDIS_PORT, REDIS_TIMEOUT, null, Redis_USE_TLS);
		return instance;
	}
}
