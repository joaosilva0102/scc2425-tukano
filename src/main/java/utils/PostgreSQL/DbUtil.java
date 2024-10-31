package utils.PostgreSQL;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Properties;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;

/**
 *
 * Inspired by https://learn.microsoft.com/en-us/azure/cosmos-db/postgresql/quickstart-app-stacks-java
 */

public class DbUtil {
    private static final String DB_USERNAME = "db.username";
    private static final String DB_PASSWORD = "db.password";
    private static final String DB_URL = "db.url";
    private static final String DB_DRIVER_CLASS = "driver.class.name";
    private static Properties properties =  null;
    private static HikariDataSource datasource;

    static {
        try {
            properties = new Properties();
            properties.load(new FileInputStream("src/main/resources/application.properties"));

            datasource = new HikariDataSource();
            datasource.setDriverClassName(properties.getProperty(DB_DRIVER_CLASS ));
            datasource.setJdbcUrl(properties.getProperty(DB_URL));
            datasource.setUsername(properties.getProperty(DB_USERNAME));
            datasource.setPassword(properties.getProperty(DB_PASSWORD));
            datasource.setMinimumIdle(100);
            datasource.setMaximumPoolSize(1000000000);
            datasource.setAutoCommit(true);
            datasource.setLoginTimeout(3);
        } catch (IOException | SQLException  e) {
            e.printStackTrace();
        }
    }
    public static DataSource getDataSource() {
        return datasource;
    }
}