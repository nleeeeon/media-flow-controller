package dao.youtube;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;

import dto.response.SearchKeywordResult;
import infrastructure.db.Db;
import infrastructure.db.DbVendorChecker;
import infrastructure.db.DbVendorChecker.DbVendor;
public class Search_keyword_DAO {
	private Connection c() throws SQLException {
		return Db.get();
	}
	private static final Gson gson = new Gson();
	
	 public List<SearchKeywordResult> allFind(int offset, int limit) throws SQLException {
	        String sql = """
	            SELECT cache_key, created_at
	            FROM search_keyword_cache
	            ORDER BY created_at DESC
	            LIMIT ? OFFSET ?
	        """;
	        List<SearchKeywordResult> rows = new ArrayList<>();
	        try (Connection c = c();
	             PreparedStatement ps = c.prepareStatement(sql)) {
	            ps.setInt(1, Math.max(1, limit));
	            ps.setInt(2, Math.max(0, offset));
	            try (ResultSet rs = ps.executeQuery()) {
	                while (rs.next()) {
	                    rows.add(new SearchKeywordResult(rs.getString("cache_key"),
	                                        rs.getTimestamp("created_at")));
	                }
	            }
	        }
	        return rows;
	    }
	
	public SearchKeywordResult find(String q) {
		String sql = "SELECT value_text, created_at FROM search_keyword_cache WHERE cache_key = ?";
		try (Connection conn = c();
				PreparedStatement ps = conn.prepareStatement(sql)) {
		        ps.setString(1, q);
		        
	        try (ResultSet rs = ps.executeQuery()) {
	        	if (rs.next()) {
	        		String valueText = rs.getString("value_text");
	                java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
	                return new SearchKeywordResult(valueText, createdAt);
	            } else {
	                return null;  // 見つからなければ null
	            }
	        }
	        
	    }catch(Exception e) {
	    	e.printStackTrace();
	    	return null;
	    }
	}
	
	// ★ 修正: save
	public boolean save(String key, List<String> videoIds){
	    String json = gson.toJson(videoIds); // List<String> → JSON文字列に変換

	    DbVendor v = DbVendorChecker.get();
	    String sql = buildSearchKeywordCacheUpsertSql(v);

	    try (Connection conn = c();
	         PreparedStatement ps = conn.prepareStatement(sql)) {
	        ps.setString(1, key);
	        ps.setString(2, json);

	        int result = ps.executeUpdate();

	        if (result > 0) {
	            System.out.println("UPSERT は実行されました");
	            return true;
	        } else {
	            System.out.println("⚠️ UPSERT 失敗: 行は追加/更新されませんでした");
	            return false;
	        }

	    } catch(Exception e) {
	        e.printStackTrace();
	        return false;
	    }
	}

	
	// ★ 追加: UPSERT 用 SQL ビルダー
	private String buildSearchKeywordCacheUpsertSql(DbVendor v) {
	    return switch (v) {
	        case MARIADB -> """
	            INSERT INTO search_keyword_cache (cache_key, value_text, created_at)
	            VALUES (?, ?, NOW(3))
	            ON DUPLICATE KEY UPDATE
	              value_text = VALUES(value_text),
	              created_at = VALUES(created_at)
	            """;
	        case POSTGRES -> """
	            INSERT INTO search_keyword_cache (cache_key, value_text, created_at)
	            VALUES (?, ?, CURRENT_TIMESTAMP(3))
	            ON CONFLICT (cache_key) DO UPDATE SET
	              value_text = EXCLUDED.value_text,
	              created_at = EXCLUDED.created_at
	            """;
	        default -> throw new IllegalStateException("未対応のDBベンダ: " + v);
	    };
	}

}

