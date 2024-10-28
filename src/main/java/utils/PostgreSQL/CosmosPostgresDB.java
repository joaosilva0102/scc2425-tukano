package utils.PostgreSQL;

import com.azure.cosmos.CosmosException;
import tukano.api.Entity;
import tukano.api.Result;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class CosmosPostgresDB<T> {
    private static final Logger log = Logger.getLogger(CosmosPostgresDB.class.getName());
    private final Connection connection;

    private CosmosPostgresDB() throws SQLException {
        this.connection = DbUtil.getDataSource().getConnection();
    }

    public <T> Result<T> insertOne(T entity) throws SQLException, IllegalAccessException {
        PreparedStatement insertStatement = connection.prepareStatement("INSERT INTO ? VALUES (?)");
        Field[] fields = entity.getClass().getDeclaredFields(); //be careful with the order of things
        insertStatement.setString(1, entity.getClass().getName());

        for (int i = 1; i < fields.length+1; i++) {
            Field field = fields[i];
            field.setAccessible(true);
            Object value = field.get(entity);

            if (value instanceof Integer) {
                insertStatement.setInt(i + 1, (Integer) value);
            } else if (value instanceof String) {
                insertStatement.setString(i + 1, (String) value);
            } else if (value instanceof Double) {
                insertStatement.setDouble(i + 1, (Double) value);
            } else if (value instanceof Boolean) {
                insertStatement.setBoolean(i + 1, (Boolean) value);
            } else if (value instanceof Float) {
                insertStatement.setFloat(i + 1, (Float) value);
            } else if (value instanceof Long) {
                insertStatement.setLong(i + 1, (Long) value);
            } else {
                throw new SQLException("Unsupported field type: " + field.getType());
            }
        }

        return insertStatement.executeUpdate() != 0 ? Result.ok() : Result.error(Result.ErrorCode.BAD_REQUEST);
    }

    public <T> Result<T> getOne(String id, Class<T> clazz) throws SQLException {
        log.info("Read data");
        PreparedStatement readStatement = connection.prepareStatement("SELECT * FROM ? WHERE id = ?");
        readStatement.setString(1, clazz.getName());
        readStatement.setString(2, id);
        ResultSet resultSet = readStatement.executeQuery();
        if (!resultSet.next()) {
            log.info("There is no data in the database!");
            return Result.error(Result.ErrorCode.NOT_FOUND);
        }
        return Result.ok(resultSet.getObject(1, clazz));
    }

    public <T> Result <T> updateOne(T entity) throws SQLException {
        log.info("Update data");
        PreparedStatement updateStatement = connection.prepareStatement("UPDATE ? SET ?");
        ResultSet resultSet = updateStatement.executeQuery();
        if (!resultSet.next()) {
            log.info("There is no data in the database!");
            return Result.error(Result.ErrorCode.NOT_FOUND);
        }
        return Result.ok(/* TODO */);
    }

    public <T> Result<?> deleteOne(Entity obj) throws SQLException {
        log.info("Delete data");
        PreparedStatement deleteStatement = connection.prepareStatement("DELETE FROM ? WHERE id = ?");
        deleteStatement.setString(1, obj.getId());
        ResultSet resultSet = deleteStatement.executeQuery();
        if (!resultSet.next()) {
            log.info("There is no data in the database!");
            return Result.error(Result.ErrorCode.NOT_FOUND);
        }
        return Result.ok(/* TODO */);
    }

    public <T> Result<List<T>> query(Class<T> clazz, String queryStr) throws SQLException {
        log.info("Querying data");
        PreparedStatement queryStatement = connection.prepareStatement(queryStr);
        List<T> items = new ArrayList<>();

        try (ResultSet rs = queryStatement.executeQuery()) {
            while (rs.next()) items.add(rs.unwrap(clazz)); // TODO Probably wrong, not tested
        }
        return Result.ok(items);
    }

    <T> Result<T> tryCatch(Supplier<T> supplierFunc) {
        try {
            return Result.ok(supplierFunc.get());
        } catch (CosmosException ce) {
            System.err.println(ce.getMessage());
            return Result.error(errorCodeFromStatus(ce.getStatusCode()));
        } /*catch (Exception x) {
			x.printStackTrace();
			return Result.error(ErrorCode.INTERNAL_ERROR);
		}*/
    }

    static Result.ErrorCode errorCodeFromStatus(int status) {
        return switch (status) {
            case 200 -> Result.ErrorCode.OK;
            case 404 -> Result.ErrorCode.NOT_FOUND;
            case 409 -> Result.ErrorCode.CONFLICT;
            default -> Result.ErrorCode.INTERNAL_ERROR;
        };
    }
}