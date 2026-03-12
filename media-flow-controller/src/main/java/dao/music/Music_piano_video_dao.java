package dao.music;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dto.music.MusicPianoVideo;
import infrastructure.db.Db;
import infrastructure.db.DbVendorChecker;
import infrastructure.db.DbVendorChecker.DbVendor;

public class Music_piano_video_dao {

    private Connection c() throws SQLException {
        return Db.get();
    }

    private static final DbVendor VENDOR = DbVendorChecker.get();

    /**
     * ① Map<Long musicId, String videoId> を一括upsert
     * confidenceは別Mapで受け取る
     */
    public void upsertBatch(
            Map<String, Long> videoToMusicMap,
            Map<String, Double> confidenceMap
    ) throws SQLException {

        if (videoToMusicMap == null || videoToMusicMap.isEmpty()) return;

        final String sql;

        if (VENDOR == DbVendor.POSTGRES) {
            sql = """
                INSERT INTO music_piano_video (
                    music_id, video_id, confidence
                ) VALUES (?,?,?)
                ON CONFLICT (music_id, video_id) DO UPDATE SET
                    confidence = EXCLUDED.confidence
                """;
        } else {
            sql = """
                INSERT INTO music_piano_video (
                    music_id, video_id, confidence
                ) VALUES (?,?,?)
                ON DUPLICATE KEY UPDATE
                    confidence = VALUES(confidence)
                """;
        }

        try (var con = c(); var ps = con.prepareStatement(sql)) {

            int count = 0;

            for (var entry : videoToMusicMap.entrySet()) {

                String videoId = entry.getKey();
                Long musicId = entry.getValue();
                Double confidence = confidenceMap.get(videoId);

                ps.setLong(1, musicId);
                ps.setString(2, videoId);

                if (confidence == null) {
                    ps.setNull(3, Types.DOUBLE);
                } else {
                    ps.setDouble(3, confidence);
                }

                ps.addBatch();

                if (++count % 500 == 0) {
                    ps.executeBatch();
                }
            }

            ps.executeBatch();
        }
    }
    
    public void upsertBatch(
            Map<String, Long> videoToMusicMap
    ) throws SQLException {

        if (videoToMusicMap == null || videoToMusicMap.isEmpty()) return;

        final String sql;

        if (VENDOR == DbVendor.POSTGRES) {
            sql = """
                INSERT INTO music_piano_video (
                    music_id, video_id, confidence
                ) VALUES (?,?,?)
                ON CONFLICT (music_id, video_id) DO UPDATE SET
                    confidence = EXCLUDED.confidence
                """;
        } else {
            sql = """
                INSERT INTO music_piano_video (
                    music_id, video_id, confidence
                ) VALUES (?,?,?)
                ON DUPLICATE KEY UPDATE
                    confidence = VALUES(confidence)
                """;
        }

        try (var con = c(); var ps = con.prepareStatement(sql)) {

            int count = 0;

            for (var entry : videoToMusicMap.entrySet()) {

                String videoId = entry.getKey();
                long musicId = entry.getValue();

                ps.setLong(1, musicId);
                ps.setString(2, videoId);
                ps.setNull(3, Types.DOUBLE); // ★ NULL登録

                ps.addBatch();

                if (++count % 500 == 0) {
                    ps.executeBatch();
                }
            }

            ps.executeBatch();
        }
    }

    /**
     * ② video_idから削除
     */
    public void deleteByVideoId(String videoId) throws SQLException {

        final String sql = """
            DELETE FROM music_piano_video
            WHERE video_id = ?
        """;

        try (var con = c(); var ps = con.prepareStatement(sql)) {
            ps.setString(1, videoId);
            ps.executeUpdate();
        }
    }

    /**
     * ③ music_idに対応するピアノ動画をconfidence降順で取得
     */
    public List<MusicPianoVideo> findByMusicIdOrderByConfidenceDesc(long musicId)
            throws SQLException {

        final String sql = """
            SELECT music_id, video_id, confidence
            FROM music_piano_video
            WHERE music_id = ?
            ORDER BY confidence IS NULL, confidence DESC
        """;

        List<MusicPianoVideo> list = new ArrayList<>();

        try (var con = c(); var ps = con.prepareStatement(sql)) {

            ps.setLong(1, musicId);

            try (var rs = ps.executeQuery()) {
                while (rs.next()) {

                    Double conf = rs.getObject("confidence") == null
                            ? null
                            : rs.getDouble("confidence");

                    list.add(new MusicPianoVideo(
                            rs.getLong("music_id"),
                            rs.getString("video_id"),
                            conf
                    ));
                }
            }
        }

        return list;
    }

    /**
     * ④ music_idに対応するconfidence最大のレコードを単体取得
     */
    public MusicPianoVideo findTopByMusicId(long musicId)
            throws SQLException {

        final String sql = """
            SELECT music_id, video_id, confidence
            FROM music_piano_video
            WHERE music_id = ?
            ORDER BY confidence IS NULL, confidence DESC
            LIMIT 1
        """;

        try (var con = c(); var ps = con.prepareStatement(sql)) {

            ps.setLong(1, musicId);

            try (var rs = ps.executeQuery()) {

                if (!rs.next()) return null;

                Double conf = rs.getObject("confidence") == null
                        ? null
                        : rs.getDouble("confidence");

                return new MusicPianoVideo(
                        rs.getLong("music_id"),
                        rs.getString("video_id"),
                        conf
                );
            }
        }
    }
}
