package utils;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.azure.cosmos.CosmosContainer;

import tukano.api.Result;

public class DB {

	public static <T> List<T> sql(String query, Class<T> clazz) {
		return CosmosDBLayer.getInstance().query(clazz, query).value();
	}

	public static <T> List<T> sql(Class<T> clazz, String fmt, Object... args) {
		return CosmosDBLayer.getInstance().query(clazz, String.format(fmt, args)).value();
	}

	public static <T> Result<T> getOne(String id, Class<T> clazz) {
		return CosmosDBLayer.getInstance().getOne(id, clazz);
	}

	public static <T> Result<?> deleteOne(T obj) {
		return CosmosDBLayer.getInstance().deleteOne(obj);
	}

	public static <T> Result<T> updateOne(T obj) {
		return CosmosDBLayer.getInstance().updateOne(obj);
	}

	public static <T> Result<T> insertOne(T obj) {
		return Result.errorOrValue(CosmosDBLayer.getInstance().insertOne(obj), obj);
	}

	public static <T> Result<T> transaction(Consumer<CosmosContainer> c, Class<T> clazz) {
		return CosmosDBLayer.getInstance().execute(c::accept, clazz);
	}

	public static <T> Result<T> transaction(Function<CosmosContainer, Result<T>> func, Class<T> clazz) {
		return CosmosDBLayer.getInstance().execute(func, clazz);
	}
}
