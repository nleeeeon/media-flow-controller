//src/main/java/app/dao/UsersDao.java (MariaDB版)
package dao.user;

import java.sql.Connection;
import java.sql.SQLException;

import infrastructure.db.Db;
import infrastructure.db.DbVendorChecker;
import infrastructure.db.DbVendorChecker.DbVendor;

public class UsersDao {
	private Connection c() throws SQLException {
		return Db.get();
	}
	// DAO クラスの先頭あたりに追加しておく
	private static final DbVendor VENDOR = DbVendorChecker.get();
	
	/** users の先頭1件を取り、その id を返す。無ければ -1 を返す。 */
	public long findAnyUserId() {
	    final String sql = "SELECT id FROM users ORDER BY id LIMIT 1";

	    try (var con = c();
	         var ps = con.prepareStatement(sql);
	         var rs = ps.executeQuery()) {

	        if (rs.next()) {
	            return rs.getLong(1); // 最初の1件
	        }
	        return -1; // レコードがなかった

	    } catch (SQLException e) {
	        throw new RuntimeException(e);
	    }
	}


/** sub に対応する uid を返す。無ければ作って返す（MariaDB最適解）。 */
	public long ensureUidForSub(String sub) {
	    if (sub == null || sub.isBlank()) {
	        throw new IllegalArgumentException("sub empty");
	    }

	    // users テーブル:
	    //   id   : PK (AUTO_INCREMENT or SERIAL / IDENTITY)
	    //   sub  : UNIQUE
	    try (var con = c()) {
	        if (VENDOR == DbVendor.POSTGRES) {
	            // PostgreSQL 用: INSERT ... ON CONFLICT ... RETURNING id
	            final String upsertPg = """
	                INSERT INTO users(sub)
	                VALUES (?)
	                ON CONFLICT (sub)
	                DO UPDATE SET sub = EXCLUDED.sub
	                RETURNING id
	                """;
	            try (var ps = con.prepareStatement(upsertPg)) {
	                ps.setString(1, sub);
	                try (var rs = ps.executeQuery()) {
	                    if (rs.next()) {
	                        return rs.getLong(1);  // 新規 or 既存どちらでも id が返る
	                    }
	                }
	            }
	            throw new SQLException("INSERT ... RETURNING id returned no row");
	        } else {
	            // MariaDB / MySQL 用: もとの LAST_INSERT_ID パターンをそのまま使う
	            final String upsertMy = """
	                INSERT INTO users(sub) VALUES (?)
	                ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)
	                """;
	            try (var ps = con.prepareStatement(upsertMy)) {
	                ps.setString(1, sub);
	                ps.executeUpdate();
	            }

	            // 同一コネクションで LAST_INSERT_ID() を取得
	            try (var st = con.createStatement();
	                 var rs = st.executeQuery("SELECT LAST_INSERT_ID()")) {
	                if (rs.next()) {
	                    return rs.getLong(1);
	                }
	            }
	            throw new SQLException("SELECT LAST_INSERT_ID() returned no row");
	        }
	    } catch (SQLException e) {
	        throw new RuntimeException(e);
	    }
	}


/** 互換版：INSERT IGNORE + SELECT。上が使えない場合の代替。 */
	/** 互換版：INSERT IGNORE / ON CONFLICT DO NOTHING + SELECT。 */
	public long ensureUidForSubCompat(String sub) {
	    if (sub == null || sub.isBlank()) {
	        throw new IllegalArgumentException("sub empty");
	    }

	    try (var con = c()) {
	        con.setAutoCommit(false);
	        try {
	            if (VENDOR == DbVendor.POSTGRES) {
	                // PostgreSQL: 既に存在する場合は何もしない
	                final String insPg = """
	                    INSERT INTO users(sub)
	                    VALUES (?)
	                    ON CONFLICT (sub) DO NOTHING
	                    """;
	                try (var ins = con.prepareStatement(insPg)) {
	                    ins.setString(1, sub);
	                    ins.executeUpdate(); // 既存なら 0 行、新規なら 1 行
	                }
	            } else {
	                // MariaDB / MySQL: 既存なら無視
	                try (var ins = con.prepareStatement(
	                        "INSERT IGNORE INTO users(sub) VALUES (?)")) {
	                    ins.setString(1, sub);
	                    ins.executeUpdate(); // 既存なら 0 行、新規なら 1 行
	                }
	            }

	            // 共通: SELECT で id を取得
	            try (var sel = con.prepareStatement(
	                    "SELECT id FROM users WHERE sub = ?")) {
	                sel.setString(1, sub);
	                try (var rs = sel.executeQuery()) {
	                    if (rs.next()) {
	                        long id = rs.getLong(1);
	                        con.commit();
	                        return id;
	                    }
	                }
	            }

	            con.rollback();
	            throw new SQLException("Failed to resolve uid");

	        } catch (SQLException e) {
	            con.rollback();
	            throw e;
	        } finally {
	            // 呼び出し側に影響しないように戻しておくならここで true に戻す
	            // con.setAutoCommit(true);
	        }

	    } catch (SQLException e) {
	        throw new RuntimeException(e);
	    }
	}



}
