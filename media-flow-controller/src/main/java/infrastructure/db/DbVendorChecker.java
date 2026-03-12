package infrastructure.db;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Locale;

public final class DbVendorChecker {

    public enum DbVendor {
        MARIADB,
        POSTGRES,
        OTHER
    }

    private static DbVendor vendor = null;

    private DbVendorChecker() {}

    // 初回だけ DB 種類を判定して静的に保持
    public static synchronized void init(Connection con) throws SQLException {
        if (vendor != null) return; // すでに判定済み

        DatabaseMetaData md = con.getMetaData();
        String name = md.getDatabaseProductName().toLowerCase(Locale.ROOT);

        if (name.contains("postgres")) {
            vendor = DbVendor.POSTGRES;
        } else if (name.contains("mariadb") || name.contains("mysql")) {
            vendor = DbVendor.MARIADB;
        } else {
            vendor = DbVendor.OTHER;
        }
        System.out.println("DB Vendor Detected = " + vendor);
    }

    public static DbVendor get() {
        if (vendor == null) {
            throw new IllegalStateException("DbVendorChecker.init() が実行されていません");
        }
        return vendor;
    }
}
