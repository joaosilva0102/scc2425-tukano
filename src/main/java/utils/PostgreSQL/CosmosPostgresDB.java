package utils.PostgreSQL;

import com.azure.cosmos.CosmosException;

import com.azure.cosmos.models.PartitionKey;
import tukano.api.Result;
import tukano.api.User;
import tukano.api.Short;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.sql.*;
import java.util.*;

/**
 *
 * Inspired by https://learn.microsoft.com/en-us/azure/cosmos-db/postgresql/quickstart-app-stacks-java
 */


public class CosmosPostgresDB<T> {
    private static final Logger log = Logger.getLogger(CosmosPostgresDB.class.getName());
    private static Connection connection;
    private static CosmosPostgresDB<?> instance;

    /*public CosmosPostgresDB(){
        initializeConnection();
    }*/

    // Private constructor for singleton
    private CosmosPostgresDB() {
        initializeConnection();
    }

    // Singleton
    public static <T> CosmosPostgresDB<T> getInstance() {
        if (instance == null) {
            synchronized (CosmosPostgresDB.class) {
                if (instance == null) {
                    instance = new CosmosPostgresDB<>();
                }
            }
        }
        return (CosmosPostgresDB<T>) instance;
    }

    private static void initializeConnection() {
        try {
            log.info("Initializing database connection...");
            if (connection == null || connection.isClosed()) {
                connection = DbUtil.getDataSource().getConnection();
                if (connection == null) {
                    throw new SQLException("Failed to obtain connection from data source");
                }
                log.info("Database connection initialized successfully");
                initializeSchema();
            }
        } catch (SQLException e) {
            log.severe("Failed to initialize connection: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Database connection failed", e);
        }
    }

    private static void initializeSchema() {
        log.info("Connecting to the database");
        try {
            log.info("Connecting to the database");
            Connection connection = DbUtil.getDataSource().getConnection();
            System.out.println("The Connection Object is of Class: " + connection.getClass());
            log.info("Database connection test: " + connection.getCatalog());
            log.info("Creating table");
            log.info("Creating index");
            log.info("distributing table");
            Scanner scanner = new Scanner(DemoApplication.class.getClassLoader().getResourceAsStream("schema.sql"));
            Statement statement = connection.createStatement();
            while (scanner.hasNextLine()) {
                statement.execute(scanner.nextLine());
            }

            log.info("Schema initialization completed successfully");
        } catch (SQLException e) {
            log.log(Level.WARNING, "Failed to initialize schema: ", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    public static Result<?> insertUser(User user) {
        checkConnection();
        log.info("Inserting user");
        try (PreparedStatement insertStatement = connection.prepareStatement(
                "INSERT INTO users(userId, email, password, displayName) VALUES (?, ?, ?, ?) ON CONFLICT (userId) DO NOTHING;"
        )) {
            insertStatement.setString(1, user.getUserId());
            insertStatement.setString(2, user.getEmail());
            insertStatement.setString(3, user.getPwd());
            insertStatement.setString(4, user.getDisplayName());
            insertStatement.executeUpdate();
            log.info("User inserted successfully");
            return Result.ok(user);
        } catch (SQLException e) {
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    private static void checkConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                log.info("Reconnecting to database");
                connection = DbUtil.getDataSource().getConnection();
                initializeSchema();
            }
        } catch (SQLException e) {
            log.info("Failed to check connection: " + e);
            throw new RuntimeException("Database connection check failed", e);
        }
    }
    public static Short insertShort(Short shortObj) {
        try {
            PreparedStatement insertStatement = connection.prepareStatement("INSERT INTO shorts(id, ownerId, blobUrl, timestamp, totalLikes ) VALUES (?, ?, ?, ?, ?)");
            insertStatement.setString(1, shortObj.getShortId());
            insertStatement.setString(2, shortObj.getOwnerId());
            insertStatement.setString(3, shortObj.getBlobUrl());
            insertStatement.setLong(4, shortObj.getTimestamp());
            insertStatement.setInt(5, shortObj.getTotalLikes());
            insertStatement.executeUpdate();
            return Result.ok(shortObj).value();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> Result<T> insertOne(T entity) throws SQLException, IllegalAccessException {
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

    public static <T> Result<T> getOne(String id, Class<T> clazz) throws SQLException {
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

    public static <T> Result <T> updateOne(T entity) throws SQLException {
        log.info("Update data");
        PreparedStatement updateStatement = connection.prepareStatement("UPDATE ? SET ?");
        ResultSet resultSet = updateStatement.executeQuery();
        if (!resultSet.next()) {
            log.info("There is no data in the database!");
            return Result.error(Result.ErrorCode.NOT_FOUND);
        }
        return Result.ok(/* TODO */);
    }

    public static<T> Result<?> deleteOne(T entity) throws SQLException {
        log.info("Delete data");
        PreparedStatement deleteStatement = connection.prepareStatement("DELETE FROM ? WHERE id = ?");
//        deleteStatement.setString(1, obj.getId());
        Field[] fields = entity.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (field.getName().equals("id")) {
                deleteStatement.setString(2, field.toString());
            }
        }
        ResultSet resultSet = deleteStatement.executeQuery();
        if (!resultSet.next()) {
            log.info("There is no data in the database!");
            return Result.error(Result.ErrorCode.NOT_FOUND);
        }
        return Result.ok(/* TODO */);
    }

    public static <T> List<T> query(Class<T> clazz, String queryStr) throws SQLException {
        log.info("Querying data");
        PreparedStatement queryStatement = connection.prepareStatement(queryStr);
        List<T> items = new ArrayList<>();

        try (ResultSet rs = queryStatement.executeQuery()) {
            while (rs.next()) items.add(rs.unwrap(clazz)); // TODO Probably wrong, not tested
        }
        return items;
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