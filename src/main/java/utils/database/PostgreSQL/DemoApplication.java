package utils.database.PostgreSQL;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Scanner;
import java.util.logging.Logger;

public class DemoApplication {

    private static final Logger log;

    static {
        System.setProperty("java.util.logging.SimpleFormatter.format", "[%4$-7s] %5$s %n");
        log =Logger.getLogger(DemoApplication.class.getName());
    }
    public static void main(String[] args)throws Exception
    {
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
        log.info("Closing database connection");
        connection.close();
    }

}