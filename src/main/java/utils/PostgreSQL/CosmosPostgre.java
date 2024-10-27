package utils.PostgreSQL;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CosmosPostgre<T> {
    private static final Logger log = Logger.getLogger(CosmosPostgre.class.getName());
    private Connection connection = null;

    private CosmosPostgre() throws SQLException {
        this.connection = DbUtil.getDataSource().getConnection();
    }

    public <T> void insertData(T entity, String sqlStatement, Connection connection) throws SQLException, IllegalAccessException {
        PreparedStatement insertStatement = connection.prepareStatement(sqlStatement);
        Field[] fields = entity.getClass().getDeclaredFields(); //be careful with the order of things

        for (int i = 0; i < fields.length; i++) {
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
        insertStatement.executeUpdate();
    }
    /*private static Pharmacy readData(Connection connection) throws SQLException {
        log.info("Read data");
        PreparedStatement readStatement = connection.prepareStatement("SELECT * FROM Pharmacy;");
        ResultSet resultSet = readStatement.executeQuery();
        if (!resultSet.next()) {
            log.info("There is no data in the database!");
            return null;
        }
        Pharmacy todo = new Pharmacy();
        todo.setpharmacy_id(resultSet.getInt("pharmacy_id"));
        todo.setpharmacy_name(resultSet.getString("pharmacy_name"));
        todo.setcity(resultSet.getString("city"));
        todo.setstate(resultSet.getString("state"));
        todo.setzip_code(resultSet.getInt("zip_code"));
        log.info("Data read from the database: " + todo.toString());
        return todo;
    }*/
}