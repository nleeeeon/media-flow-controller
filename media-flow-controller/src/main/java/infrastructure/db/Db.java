package infrastructure.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Db {
    
    
    private static final String URL =
        System.getenv().getOrDefault("DB_URL",
            "");

    private static final String USER =
        System.getenv().getOrDefault("DB_USER", "");

    private static final String PASS =
        System.getenv().getOrDefault("DB_PASSWORD", "");

    

    public static Connection get() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }
}
