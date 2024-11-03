package utils.database;

import java.util.List;
import tukano.api.Result;

public class PostgresDB {

    public static <T> List<T> sql(String query, Class<T> clazz) {
        return Hibernate.getInstance().sql(query, clazz);
    }

    public static <T> List<T> sql(Class<T> clazz, String fmt, Object ... args) {
        return Hibernate.getInstance().sql(String.format(fmt, args), clazz);
    }

    public static <T> Result<T> getOne(String id, Class<T> clazz) {
        return Hibernate.getInstance().getOne(id, clazz);
    }

    public static <T> Result<T> deleteOne(T obj) {
        return Hibernate.getInstance().deleteOne(obj);
    }

    public static <T> Result<T> updatene(T obj) {
        return Hibernate.getInstance().updateOne(obj);
    }

    public static <T> Result<T> insertOne(T obj) {
        return Result.errorOrValue(Hibernate.getInstance().insertOne(obj), obj);
    }
}