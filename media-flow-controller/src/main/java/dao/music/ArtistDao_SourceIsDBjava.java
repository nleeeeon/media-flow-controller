package dao.music;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import infrastructure.db.Db;
import infrastructure.db.DbVendorChecker;
import infrastructure.db.DbVendorChecker.DbVendor;
import infrastructure.index.ArtistSearchIndex.Artist;

public class ArtistDao_SourceIsDBjava {
	
	
	
	private Connection c() throws SQLException {
		return Db.get();
	}
	
	// すべて取得：同じ id の複数行を 1 Artist にまとめ、最初の行を正名、それ以外を aliases に格納
	public List<Artist> allFind() throws SQLException {
	    Map<String, Artist> map = new LinkedHashMap<>(); // 順序保持
	    try (var con = c()) {
	        String q = "SELECT id, name FROM artist ORDER BY id, name";
	        try (var ps = con.prepareStatement(q); var rs = ps.executeQuery()) {
	            while (rs.next()) {
	                String id  = rs.getString("id");
	                String nm  = rs.getString("name");

	                var a = map.get(id);
	                if (a == null) {
	                    // 最初に来た name を正名にする。aliases は空で開始
	                    a = new Artist(id, nm, null);
	                    map.put(id, a);
	                } else {
	                    if (nm != null && !nm.equals(a.name)) {
	                        // 既に正名と同じなら無視、違えば alias として追加（重複チェック）
	                        if (!a.aliases.contains(nm)) a.aliases.add(nm);
	                    }
	                }
	            }
	        }
	    } catch (Exception e) {
	        e.printStackTrace();
	        return java.util.List.of();
	    }
	    return new ArrayList<>(map.values());
	}
	
	public Map<String, List<String>> allFindAsMap() throws SQLException {
	    Map<String, List<String>> map = new LinkedHashMap<>();

	    try (var con = c()) {
	        String q = "SELECT id, name FROM artist ORDER BY id, name";
	        try (var ps = con.prepareStatement(q);
	             var rs = ps.executeQuery()) {

	            while (rs.next()) {
	                String id = rs.getString("id");
	                String nm = rs.getString("name");

	                // まだ登録されていない id なら、新しい List を作る
	                List<String> list = map.get(id);
	                if (list == null) {
	                    list = new ArrayList<>();
	                    if (nm != null) list.add(nm); // 最初の name は正名
	                    map.put(id, list);
	                } else {
	                    // 2つ目以降 → alias として追加（重複チェックあり）
	                    if (nm != null && !list.contains(nm)) {
	                        list.add(nm);
	                    }
	                }
	            }
	        }
	    } catch (Exception e) {
	        e.printStackTrace();
	        return Map.of();
	    }

	    return map;
	}

	
	/** 
	 * artist テーブルの全レコードを削除する 
	 */
	public void deleteAll() throws SQLException {
	    String sql = "DELETE FROM artist";
	    try (var con = c(); var ps = con.prepareStatement(sql)) {
	        int count = ps.executeUpdate();
	        ///System.out.println("artist テーブルから " + count + " 件を削除しました。");
	    } catch (SQLException e) {
	        e.printStackTrace();
	        throw e;
	    }
	}
	
	
	// MariaDB: INSERT IGNORE
    // PostgreSQL: ON CONFLICT DO NOTHING
    public void upsert(Map<String, List<String>> artists) {
        if (artists == null || artists.isEmpty()) return;

        try (var con = c()) {
            DbVendor v = DbVendorChecker.get();

            final String sql;
            switch (v) {
                case MARIADB -> 
                    sql = "INSERT IGNORE INTO artist (id, name) VALUES (?, ?)";
                case POSTGRES ->
                    sql = "INSERT INTO artist (id, name) VALUES (?, ?) ON CONFLICT DO NOTHING";
                default ->
                    // 必要ならここを別実装に変えてもよい
                    throw new IllegalStateException("未対応のDBベンダ: " + v);
            }

            try (var ps = con.prepareStatement(sql)) {
                int n = 0;
                for (Map.Entry<String, List<String>> e : artists.entrySet()) {
                    String id = e.getKey();
                    if (id == null || id.isBlank()) continue;

                    for (String name : e.getValue()) {
                        if (name == null || name.isBlank()) continue;

                        ps.setString(1, id);
                        ps.setString(2, name);
                        ps.addBatch();

                        if ((++n % 500) == 0) {
                            ps.executeBatch();
                        }
                    }
                }
                if (n > 0) {
                    ps.executeBatch();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
	
	public void deleteAliasesByArtistKey(String artistKey, String name) {
		String sql = "DELETE FROM artist WHERE id = ? AND name = ?";
		try (var con = c(); var ps = con.prepareStatement(sql)) {
		  ps.setString(1, artistKey);
		  ps.setString(2, name);
		  ps.executeUpdate();
		}catch(Exception e) {
	    	e.printStackTrace();
	    }
    }
	
	public void deleteParentByArtistKey(String artistKey) {
		String sql = "DELETE FROM artist WHERE id = ?";
		try (var con = c(); var ps = con.prepareStatement(sql)) {
		  ps.setString(1, artistKey);
		  ps.executeUpdate();
		}catch(Exception e) {
	    	e.printStackTrace();
	    }
    }
	
	// 複数 id をまとめて削除
	public void deleteParentByArtistKeys(List<String> artistKeys) {
	    if (artistKeys == null || artistKeys.isEmpty()) return;

	    final String sql = "DELETE FROM artist WHERE id = ?";
	    try (var con = c(); var ps = con.prepareStatement(sql)) {
	        int n = 0;
	        for (String id : artistKeys) {
	            if (id == null || id.isBlank()) continue;

	            ps.setString(1, id);
	            ps.addBatch();
	            if ((++n % 500) == 0) {
	                ps.executeBatch();
	            }
	        }
	        if (n > 0) {
	            ps.executeBatch();
	        }
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	}


}



