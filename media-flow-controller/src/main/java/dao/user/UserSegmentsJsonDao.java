package dao.user;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

import infrastructure.db.Db;
import infrastructure.db.DbVendorChecker;
import infrastructure.db.DbVendorChecker.DbVendor;

public class UserSegmentsJsonDao {

    private Connection c() throws SQLException { return Db.get(); }

    // --- segment_type → (json_text, start_sec) ---
    public static record SegmentData(String jsonText, Integer startSec) {}

    private static final DbVendor VENDOR = DbVendorChecker.get();

    /**
     * 指定 user/video のすべての segment_type を Map で返す。
     */
    public Map<String, SegmentData> loadJson(long userId, String videoId) {

        String sql = """
          SELECT segment_type, json_text, start_sec
          FROM user_segments_json
          WHERE user_id=? AND video_id=?
        """;

        Map<String, SegmentData> map = new HashMap<>();

        try (Connection conn = c();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, userId);
            ps.setString(2, videoId);

            try (var rs = ps.executeQuery()) {

                while (rs.next()) {

                    String type = rs.getString("segment_type");
                    String json = rs.getString("json_text");

                    Integer sec = null;
                    int v = rs.getInt("start_sec");

                    if (!rs.wasNull()) {
                        sec = v;
                    }

                    map.put(type, new SegmentData(json, sec));
                }
            }

        } catch (SQLException e) {
            System.out.println("[UserSegmentsJsonDao] load err: " + e.getMessage());
        }

        return map;
    }


    /**
     * upsert：userId + videoId + segmentType を主キーとして上書き。
     */
    public void upsertJson(long userId, String videoId, String segmentType,
                           String jsonText, Integer startSec) {

        final String sql;

        if (VENDOR == DbVendor.POSTGRES) {

            sql = """
              INSERT INTO user_segments_json(user_id, video_id, segment_type, json_text, start_sec)
              VALUES(?,?,?,?,?)
              ON CONFLICT (user_id, video_id, segment_type) DO UPDATE SET
                json_text = EXCLUDED.json_text,
                start_sec = EXCLUDED.start_sec,
                updated_at = CURRENT_TIMESTAMP
            """;

        } else {

            sql = """
              INSERT INTO user_segments_json(user_id, video_id, segment_type, json_text, start_sec)
              VALUES(?,?,?,?,?)
              ON DUPLICATE KEY UPDATE
                json_text = VALUES(json_text),
                start_sec = VALUES(start_sec),
                updated_at = CURRENT_TIMESTAMP
            """;
        }

        try (Connection conn = c();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, userId);
            ps.setString(2, videoId);
            ps.setString(3, segmentType);
            ps.setString(4, jsonText);
            ps.setObject(5, startSec, Types.INTEGER);

            ps.executeUpdate();

        } catch (SQLException e) {
            System.out.println("[UserSegmentsJsonDao] upsert err: " + e.getMessage());
        }
    }


    /**
     * 1種類だけ削除
     */
    public void deleteOne(long userId, String videoId, String segmentType) {

        String sql = """
          DELETE FROM user_segments_json
          WHERE user_id=? AND video_id=? AND segment_type=?
        """;

        try (Connection conn = c();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, userId);
            ps.setString(2, videoId);
            ps.setString(3, segmentType);

            ps.executeUpdate();

        } catch (SQLException e) {
            System.out.println("[UserSegmentsJsonDao] delete err: " + e.getMessage());
        }
    }
}
