package utils.database.PostgreSQL;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Properties;

import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;

/**
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
            //properties.load(new FileInputStream("application.properties"));
            properties.load(DbUtil.class.getClassLoader().getResourceAsStream("application.properties"));


            datasource = new HikariDataSource();
            datasource.setDriverClassName(properties.getProperty(DB_DRIVER_CLASS ));
            datasource.setJdbcUrl(properties.getProperty(DB_URL));
            datasource.setUsername(properties.getProperty(DB_USERNAME));
            datasource.setPassword(properties.getProperty(DB_PASSWORD));
            datasource.setMinimumIdle(2);
            datasource.setMaximumPoolSize(20);
            datasource.setIdleTimeout(15000);    // 15 seconds
            datasource.setMaxLifetime(180000);   // 3 minutes
            datasource.setConnectionTimeout(30000);
            datasource.setLeakDetectionThreshold(60000); // 1 minute
            datasource.setAutoCommit(true);


            datasource.addDataSourceProperty("ssl", "true");
            datasource.addDataSourceProperty("sslmode", "require");

            datasource.setLoginTimeout(3);
        } catch (IOException | SQLException e) {
            e.printStackTrace();
        }
    }
    public static DataSource getDataSource() {
        return datasource;
    }
}