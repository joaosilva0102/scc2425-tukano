package utils.database;

import java.util.List;
import tukano.api.Result;

public class DB {

	private static boolean nosql = false;

	public static <T> List<T> sql(String query, Class<T> clazz) {
		return nosql ? CosmosDB.getInstance().query(clazz, query) :
				Hibernate.getInstance().sql(query, clazz);
	}

	public static <T> List<T> sql(Class<T> clazz, String fmt, Object... args) {
		return nosql ? CosmosDB.getInstance().query(clazz, String.format(fmt, args)) :
				Hibernate.getInstance().sql(String.format(fmt, args), clazz);
	}

	public static <T> Result<T> getOne(String id, Class<T> clazz) {
		return nosql ? CosmosDB.getInstance().getOne(id, clazz) :
				Hibernate.getInstance().getOne(id, clazz);
	}

	public static <T> Result<T> deleteOne(T obj) {
		return nosql ? CosmosDB.getInstance().deleteOne(obj) :
				Hibernate.getInstance().deleteOne(obj);
	}

	public static <T> Result<T> updateOne(T obj) {
		return nosql ? CosmosDB.getInstance().updateOne(obj) :
				Hibernate.getInstance().updateOne(obj);
	}

	public static <T> Result<T> insertOne(T obj) {
		return nosql ? Result.errorOrValue(CosmosDB.getInstance().insertOne(obj), obj) :
				Result.errorOrValue(Hibernate.getInstance().insertOne(obj), obj);
	}
}
