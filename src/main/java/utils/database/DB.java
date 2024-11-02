package utils.database;

import java.util.List;
import tukano.api.Result;

public class DB {

	public static <T> List<T> sql(String query, Class<T> clazz) {
		return CosmosDB.getInstance().query(clazz, query);
	}

	public static <T> List<T> sql(Class<T> clazz, String fmt, Object... args) {
		return CosmosDB.getInstance().query(clazz, String.format(fmt, args));
	}

	public static <T> Result<T> getOne(String id, Class<T> clazz) {
		return CosmosDB.getInstance().getOne(id, clazz);
	}

	public static <T> Result<T> deleteOne(T obj) {
		return CosmosDB.getInstance().deleteOne(obj);
	}

	public static <T> Result<T> updateOne(T obj) {
		return CosmosDB.getInstance().updateOne(obj);
	}

	public static <T> Result<T> insertOne(T obj) {
		return Result.errorOrValue(CosmosDB.getInstance().insertOne(obj), obj);
	}
}
