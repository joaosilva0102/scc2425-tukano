package utils.PostgreSQL;

import com.azure.cosmos.CosmosException;
import tukano.api.Result;
import tukano.api.User;
import tukano.api.Short;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.Supplier;

/**
 * Inspired by https://learn.microsoft.com/en-us/azure/cosmos-db/postgresql/quickstart-app-stacks-java
 * Trying to solve connection issues with https://stackoverflow.com/questions/38545507/postgresql-close-connection-after-method-has-finished
 */

public class CosmosPostgresDB<T> {
    private static final Logger log = Logger.getLogger(CosmosPostgresDB.class.getName());
    private static CosmosPostgresDB<?> instance;

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
        try (Connection connection = DbUtil.getDataSource().getConnection()) {
            log.info("Initializing database connection...");
            if (connection == null || connection.isClosed()) {
                throw new SQLException("Failed to obtain connection from data source");
            }
            log.info("Database connection initialized successfully");
            initializeSchema(connection);
        } catch (SQLException e) {
            log.severe("Failed to initialize connection: " + e.getMessage());
            throw new RuntimeException("Database connection failed", e);
        }
    }

    private static void initializeSchema(Connection connection) {
        log.info("Connecting to the database");
        try (Scanner scanner = new Scanner(DemoApplication.class.getClassLoader().getResourceAsStream("schema.sql"));
             Statement statement = connection.createStatement()) {

            while (scanner.hasNextLine()) {
                statement.execute(scanner.nextLine());
            }
            log.info("Schema initialization completed successfully");

        } catch (SQLException e) {
            log.log(Level.WARNING, "Failed to initialize schema: ", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    public static <T> Result<T> insertOne(T entity) {
        log.info("Inserting data");
        return switch (entity.getClass().getSimpleName()) {
            case "User" -> (Result<T>) insertUser((User) entity);
            case "Short" -> (Result<T>) insertShort((Short) entity);
            default -> {
                log.info("Entity not recognized: " + entity.getClass().getSimpleName());
                yield Result.error(Result.ErrorCode.BAD_REQUEST);
            }
        };
    }

    private static Result<User> insertUser(User user) {
        log.info("Inserting user");
        String sql = "INSERT INTO users(userId, email, password, displayName) VALUES (?, ?, ?, ?) ON CONFLICT (userId) DO NOTHING;";
        try (Connection connection = DbUtil.getDataSource().getConnection();
             PreparedStatement insertStatement = connection.prepareStatement(sql)) {

            insertStatement.setString(1, user.getUserId());
            insertStatement.setString(2, user.getEmail());
            insertStatement.setString(3, user.getPwd());
            insertStatement.setString(4, user.getDisplayName());
            insertStatement.executeUpdate();
            return Result.ok(user);

        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to insert user", e);
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    private static Result<Short> insertShort(Short shortObj) {
        String sql = "INSERT INTO shorts(id, ownerId, blobUrl, timestamp, totalLikes) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = DbUtil.getDataSource().getConnection();
             PreparedStatement insertStatement = connection.prepareStatement(sql)) {

            insertStatement.setString(1, shortObj.getShortId());
            insertStatement.setString(2, shortObj.getOwnerId());
            insertStatement.setString(3, shortObj.getBlobUrl());
            insertStatement.setLong(4, shortObj.getTimestamp());
            insertStatement.setInt(5, shortObj.getTotalLikes());
            insertStatement.executeUpdate();
            return Result.ok(shortObj);

        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to insert short", e);
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    public static <T> Result<T> getOne(String id, Class<T> clazz) {
        log.info("Fetching data");
        return switch (clazz.getSimpleName()) {
            case "User" -> (Result<T>) getUserById(id);
            case "Short" -> (Result<T>) getShortById(id);
            default -> {
                log.info("Entity not recognized: " + clazz.getSimpleName());
                yield Result.error(Result.ErrorCode.BAD_REQUEST);
            }
        };
    }

    private static Result<User> getUserById(String id) {
        String sql = "SELECT * FROM users WHERE userId = ?";
        try (Connection connection = DbUtil.getDataSource().getConnection();
             PreparedStatement readStatement = connection.prepareStatement(sql)) {

            readStatement.setString(1, id);
            ResultSet resultSet = readStatement.executeQuery();
            if (resultSet.next()) {
                User user = new User(
                        resultSet.getString("userId"),
                        resultSet.getString("email"),
                        resultSet.getString("password"),
                        resultSet.getString("displayName"));
                return Result.ok(user);
            }
            return Result.error(Result.ErrorCode.NOT_FOUND);

        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to fetch user by ID", e);
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    private static Result<Short> getShortById(String id) {
        String sql = "SELECT * FROM shorts WHERE id = ?";
        try (Connection connection = DbUtil.getDataSource().getConnection();
             PreparedStatement readStatement = connection.prepareStatement(sql)) {

            readStatement.setString(1, id);
            ResultSet resultSet = readStatement.executeQuery();
            if (resultSet.next()) {
                Short shortObj = new Short(
                        resultSet.getString("id"),
                        resultSet.getString("ownerId"),
                        resultSet.getString("blobUrl"),
                        resultSet.getLong("timestamp"),
                        resultSet.getInt("totalLikes"));
                return Result.ok(shortObj);
            }
            return Result.error(Result.ErrorCode.NOT_FOUND);

        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to fetch short by ID", e);
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    public static <T> Result<T> updateOne(T entity) {
        log.info("Updating data");
        return switch (entity.getClass().getSimpleName()) {
            case "User" -> (Result<T>) updateUser((User) entity);
            case "Short" -> (Result<T>) updateShort((Short) entity);
            default -> {
                log.info("Entity not recognized: " + entity.getClass().getSimpleName());
                yield Result.error(Result.ErrorCode.BAD_REQUEST);
            }
        };
    }

    private static Result<User> updateUser(User user) {
        String sql = "UPDATE users SET email = ?, password = ?, displayName = ? WHERE userId = ?";
        try (Connection connection = DbUtil.getDataSource().getConnection();
             PreparedStatement updateStatement = connection.prepareStatement(sql)) {

            updateStatement.setString(1, user.getEmail());
            updateStatement.setString(2, user.getPwd());
            updateStatement.setString(3, user.getDisplayName());
            updateStatement.setString(4, user.getUserId());
            updateStatement.executeUpdate();
            return Result.ok(user);

        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to update user", e);
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    private static Result<Short> updateShort(Short shortObj) {
        String sql = "UPDATE shorts SET ownerId = ?, blobUrl = ?, timestamp = ?, totalLikes = ? WHERE id = ?";
        try (Connection connection = DbUtil.getDataSource().getConnection();
             PreparedStatement updateStatement = connection.prepareStatement(sql)) {

            updateStatement.setString(1, shortObj.getOwnerId());
            updateStatement.setString(2, shortObj.getBlobUrl());
            updateStatement.setLong(3, shortObj.getTimestamp());
            updateStatement.setInt(4, shortObj.getTotalLikes());
            updateStatement.setString(5, shortObj.getShortId());
            updateStatement.executeUpdate();
            return Result.ok(shortObj);

        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to update short", e);
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    public static <T> Result<T> deleteOne(T entity) {
        log.info("Deleting data");
        return switch (entity.getClass().getSimpleName()) {
            case "User" -> (Result<T>) deleteUser((User) entity);
            case "Short" -> (Result<T>) deleteShort((Short) entity);
            default -> {
                log.info("Entity not recognized: " + entity.getClass().getSimpleName());
                yield Result.error(Result.ErrorCode.BAD_REQUEST);
            }
        };
    }

    private static Result<User> deleteUser(User user) {
        String sql = "DELETE FROM users WHERE userId = ?";
        try (Connection connection = DbUtil.getDataSource().getConnection();
             PreparedStatement deleteStatement = connection.prepareStatement(sql)) {

            deleteStatement.setString(1, user.getUserId());
            deleteStatement.executeUpdate();
            return Result.ok(user);

        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to delete user", e);
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    private static Result<Short> deleteShort(Short shortObj) {
        String sql = "DELETE FROM shorts WHERE id = ?";
        try (Connection connection = DbUtil.getDataSource().getConnection();
             PreparedStatement deleteStatement = connection.prepareStatement(sql)) {

            deleteStatement.setString(1, shortObj.getShortId());
            deleteStatement.executeUpdate();
            return Result.ok(shortObj);

        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to delete short", e);
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    public static <T> List<T> query(Class<T> clazz, String queryStr) throws SQLException {
        log.info("Querying data");
        try (Connection connection = DbUtil.getDataSource().getConnection();
             PreparedStatement queryStatement = connection.prepareStatement(queryStr);
             ResultSet rs = queryStatement.executeQuery()) {

            List<T> items = new ArrayList<>();
            while (rs.next()) items.add(rs.unwrap(clazz));
            return items;
        }
    }

    public List<User> queryByPattern(String sqlQuery, String pattern) throws SQLException {
        log.info("Querying users by pattern: " + pattern);
        List<User> users = new ArrayList<>();

        try (Connection connection = DbUtil.getDataSource().getConnection()) {
            if (pattern == null || pattern.trim().isEmpty()) {
                sqlQuery = "SELECT * FROM public.users";
            }
            PreparedStatement queryStatement = connection.prepareStatement(sqlQuery);
                if (pattern != null && !pattern.trim().isEmpty()) {
                    String formattedPattern = "%" + pattern.toUpperCase() + "%";
                    queryStatement.setString(1, formattedPattern);
                }

                ResultSet rs = queryStatement.executeQuery();
                while (rs.next()) {
                    User user = new User();
                    user.setId(rs.getString("id"));
                    user.setUserId(rs.getString("userId"));
                    user.setEmail(rs.getString("email"));
                    user.setPwd(rs.getString("password"));
                    user.setDisplayName(rs.getString("displayName"));

                    users.add(user);
                }
            }
         catch (Exception e) {
            log.severe("Failed to query users by pattern: " + e.getMessage());
            throw new SQLException("Failed to query users by pattern", e);
        }
        return users;
    }


    // Error handling utility
    <T> Result<T> tryCatch(Supplier<T> supplierFunc) {
        try {
            return Result.ok(supplierFunc.get());
        } catch (CosmosException ce) {
            log.severe(ce.getMessage());
            return Result.error(errorCodeFromStatus(ce.getStatusCode()));
        }
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
