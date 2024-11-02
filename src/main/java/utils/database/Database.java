package utils.database;

import tukano.api.Result;

import java.util.List;

public interface Database {
    <T> T getInstance();
    <T> Result<T> getOne(String id, Class<T> clazz);
    <T> Result<T> deleteOne(T obj);
    <T> Result<T> updateOne(T obj);
    <T> Result<T> insertOne(T obj);
    <T> List<T> query(Class<T> clazz, String queryStr);
}
