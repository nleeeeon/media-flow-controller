package dao.youtube;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import infrastructure.db.Db;
import infrastructure.db.DbVendorChecker;
import infrastructure.db.DbVendorChecker.DbVendor;

public class Checked_video_ids_dao {

	private Connection c() throws SQLException {
		return Db.get();
	}
	private static final DbVendor VENDOR = DbVendorChecker.get();
	public Map<String, Boolean> findExistingVideoIds(Set<String> videoIds) throws SQLException {

	    Map<String, Boolean> result = new HashMap<>();

	    if (videoIds == null || videoIds.isEmpty()) {
	        return result;
	    }

	    final int BATCH_SIZE = 500;

	    List<String> ids = new ArrayList<>(videoIds);

	    try (var con = c()) {

	        for (int start = 0; start < ids.size(); start += BATCH_SIZE) {

	            List<String> chunk = ids.subList(
	                    start,
	                    Math.min(start + BATCH_SIZE, ids.size())
	            );

	            String placeholders =
	                    String.join(",", Collections.nCopies(chunk.size(), "?"));

	            String sql =
	                    "SELECT video_id, is_music FROM checked_video_ids WHERE video_id IN (" + placeholders + ")";

	            try (var ps = con.prepareStatement(sql)) {

	                int i = 1;
	                for (String id : chunk) {
	                    ps.setString(i++, id);
	                }

	                try (var rs = ps.executeQuery()) {

	                    while (rs.next()) {

	                        String videoId = rs.getString("video_id");
	                        boolean isMusic = rs.getBoolean("is_music");

	                        result.put(videoId, isMusic);
	                    }
	                }
	            }
	        }
	    }

	    return result;
	}
	public void upsertCheckedVideoIds(Map<String, Boolean> videoIds) throws SQLException {

	    if (videoIds == null || videoIds.isEmpty()) return;

	    final String sql;

	    if (VENDOR == DbVendor.POSTGRES) {
	        sql = """
	            INSERT INTO checked_video_ids (
	              video_id,
	              is_music
	            ) VALUES (?, ?)
	            ON CONFLICT (video_id) DO UPDATE SET
	              is_music = EXCLUDED.is_music,
	              checked_at = CURRENT_TIMESTAMP
	            """;
	    } else {
	        sql = """
	            INSERT INTO checked_video_ids (
	              video_id,
	              is_music
	            ) VALUES (?, ?)
	            ON DUPLICATE KEY UPDATE
	              is_music = VALUES(is_music),
	              checked_at = CURRENT_TIMESTAMP
	            """;
	    }

	    try (var con = c(); var ps = con.prepareStatement(sql)) {

	        int count = 0;

	        for (var entry : videoIds.entrySet()) {

	            ps.setString(1, entry.getKey());
	            ps.setBoolean(2, entry.getValue());

	            ps.addBatch();

	            if (++count % 500 == 0) {
	                ps.executeBatch();
	            }
	        }

	        ps.executeBatch();
	    }
	}
}
