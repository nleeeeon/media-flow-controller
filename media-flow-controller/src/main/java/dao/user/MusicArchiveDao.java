// MusicArchiveDao.java (MariaDB)
package dao.user;
// src/main/java/app/dao/UserSongMonthlyDao.java

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import domain.youtube.VideoGenre;
import dto.music.MonthlySongStats;
import infrastructure.db.Db;
import infrastructure.db.DbVendorChecker;
import infrastructure.db.DbVendorChecker.DbVendor;

public class MusicArchiveDao {

    private Connection c() throws SQLException {
        return Db.get();
    }

    /**
     * 単発UPSERT
     */
    public void upsertItem(long userId, MonthlySongStats item) {
        final String sql = buildUserSongMonthlyUpsertSql();

        try (var con = c(); var ps = con.prepareStatement(sql)) {
            bind(ps, userId, item);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * バルクUPSERT
     */
    public void upsertItemsBulk(long userId, List<MonthlySongStats> items) {
        if (items == null || items.isEmpty()) return;

        final String sql = buildUserSongMonthlyUpsertSql();

        try (var con = c(); var ps = con.prepareStatement(sql)) {

            int count = 0;

            for (MonthlySongStats item : items) {

                bind(ps, userId, item);
                ps.addBatch();

                if (++count % 500 == 0) {
                    ps.executeBatch();
                }
            }

            ps.executeBatch();

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 再生回数ランキング
     */
    public List<String> findTopVideoIds(
            long userId,
            String fromYm,
            String toYm,
            VideoGenre genre,
            int limit
    ) {

        StringBuilder sql = new StringBuilder();

        sql.append("""
            SELECT usm.video_id,
                   SUM(usm.plays) AS p,
                   MAX(usm.last_seen) AS lastp,
                   v.is_confident
            FROM user_song_monthly usm
            LEFT JOIN videos v ON v.video_id = usm.video_id
            WHERE usm.user_id = ?
              AND usm.ym BETWEEN ? AND ?
        """);

        if (genre != null) {
            sql.append(" AND v.genre = ? ");
        }

        sql.append("""
            GROUP BY usm.video_id, v.is_confident
            ORDER BY v.is_confident DESC, p DESC, lastp DESC
            LIMIT ?
        """);

        try (var con = c(); var ps = con.prepareStatement(sql.toString())) {

            int i = 1;

            ps.setLong(i++, userId);
            ps.setString(i++, fromYm);
            ps.setString(i++, toYm);

            if (genre != null) {
                ps.setString(i++, genre.name());
            }

            ps.setInt(i++, limit);

            ResultSet rs = ps.executeQuery();

            List<String> result = new ArrayList<>();

            while (rs.next()) {
                result.add(rs.getString("video_id"));
            }

            return result;

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * ランダム取得
     */
    public List<String> findRandomVideoIds(
            long userId,
            String fromYm,
            String toYm,
            VideoGenre genre,
            int limit
    ) {

        DbVendor v = DbVendorChecker.get();

        String orderRand =
            (v == DbVendor.POSTGRES)
            ? "ORDER BY t.is_confident DESC, RANDOM() "
            : "ORDER BY t.is_confident DESC, RAND() ";

        StringBuilder sql = new StringBuilder();

        sql.append("""
            SELECT t.video_id
            FROM (
                SELECT usm.video_id,
                       v.is_confident
                FROM user_song_monthly usm
                LEFT JOIN videos v ON v.video_id = usm.video_id
                WHERE usm.user_id = ?
                  AND usm.ym BETWEEN ? AND ?
        """);

        if (genre != null) {
            sql.append(" AND v.genre = ? ");
        }

        sql.append("""
                GROUP BY usm.video_id, v.is_confident
            ) t
        """);

        sql.append(orderRand).append("LIMIT ?");

        try (var con = c(); var ps = con.prepareStatement(sql.toString())) {

            int i = 1;

            ps.setLong(i++, userId);
            ps.setString(i++, fromYm);
            ps.setString(i++, toYm);

            if (genre != null) {
                ps.setString(i++, genre.name());
            }

            ps.setInt(i++, limit);

            ResultSet rs = ps.executeQuery();

            List<String> result = new ArrayList<>();

            while (rs.next()) {
                result.add(rs.getString(1));
            }

            return result;

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 期間統計
     */
    public Stats findStatsInRange(
            long userId,
            String videoId,
            String fromYm,
            String toYm
    ) {

        final String sql = """
            SELECT COALESCE(SUM(plays),0) AS p,
                   MIN(first_seen) AS f,
                   MAX(last_seen) AS l
            FROM user_song_monthly
            WHERE user_id = ?
              AND video_id = ?
              AND ym BETWEEN ? AND ?
        """;

        try (var con = c(); var ps = con.prepareStatement(sql)) {

            ps.setLong(1, userId);
            ps.setString(2, videoId);
            ps.setString(3, fromYm);
            ps.setString(4, toYm);

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {

                int plays = rs.getInt("p");
                Timestamp f = rs.getTimestamp("f");
                Timestamp l = rs.getTimestamp("l");

                return new Stats(
                        plays,
                        f == null ? null : f.toInstant(),
                        l == null ? null : l.toInstant()
                );
            }

            return new Stats(0, null, null);

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 累計再生
     */
    public Stats findStatsAllTime(long userId, String videoId) {

        final String sql = """
            SELECT COALESCE(SUM(plays),0) AS p
            FROM user_song_monthly
            WHERE user_id = ?
              AND video_id = ?
        """;

        try (var con = c(); var ps = con.prepareStatement(sql)) {

            ps.setLong(1, userId);
            ps.setString(2, videoId);

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return new Stats(rs.getInt("p"), null, null);
            }

            return new Stats(0, null, null);

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * video_id → genre
     */
    public VideoGenre findGenreByVideoId(String videoId) {

        final String sql = """
            SELECT genre
            FROM videos
            WHERE video_id = ?
        """;

        try (var con = c(); var ps = con.prepareStatement(sql)) {

            ps.setString(1, videoId);

            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                return null;
            }

            return VideoGenre.valueOf(rs.getString("genre"));

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 期間内の video と genre
     */
    public List<VideoWithGenre> findVideoIdsWithGenreInRange(
            long userId,
            String fromYm,
            String toYm
    ) {

        final String sql = """
            SELECT usm.video_id,
                   v.genre
            FROM user_song_monthly usm
            LEFT JOIN videos v
                   ON v.video_id = usm.video_id
            WHERE usm.user_id = ?
              AND usm.ym BETWEEN ? AND ?
            GROUP BY usm.video_id, v.genre
        """;

        try (var con = c(); var ps = con.prepareStatement(sql)) {

            ps.setLong(1, userId);
            ps.setString(2, fromYm);
            ps.setString(3, toYm);

            ResultSet rs = ps.executeQuery();

            List<VideoWithGenre> result = new ArrayList<>();

            while (rs.next()) {

                String videoId = rs.getString("video_id");
                VideoGenre genre = VideoGenre.valueOf(rs.getString("genre"));

                result.add(new VideoWithGenre(videoId, genre));
            }

            return result;

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static record Stats(int plays, Instant first, Instant last) {}

    public static record VideoWithGenre(String videoId, VideoGenre genre) {}

    private static void bind(
            PreparedStatement ps,
            long userId,
            MonthlySongStats item
    ) throws SQLException {

        ps.setLong(1, userId);
        ps.setString(2, item.ym());
        ps.setString(3, item.videoId());
        ps.setInt(4, item.plays());
        ps.setInt(5, item.daysActive());
        ps.setTimestamp(6, Timestamp.from(item.firstSeen()));
        ps.setTimestamp(7, Timestamp.from(item.lastSeen()));
    }

    private String buildUserSongMonthlyUpsertSql() {

        DbVendor v = DbVendorChecker.get();

        return switch (v) {

            case MARIADB -> """
                INSERT INTO user_song_monthly
                (user_id, ym, video_id, plays, days_active, first_seen, last_seen)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    plays       = user_song_monthly.plays + VALUES(plays),
                    days_active = GREATEST(user_song_monthly.days_active, VALUES(days_active)),
                    first_seen  = LEAST(user_song_monthly.first_seen, VALUES(first_seen)),
                    last_seen   = GREATEST(user_song_monthly.last_seen, VALUES(last_seen))
            """;

            case POSTGRES -> """
                INSERT INTO user_song_monthly
                (user_id, ym, video_id, plays, days_active, first_seen, last_seen)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id, ym, video_id)
                DO UPDATE SET
                    plays       = user_song_monthly.plays + EXCLUDED.plays,
                    days_active = GREATEST(user_song_monthly.days_active, EXCLUDED.days_active),
                    first_seen  = LEAST(user_song_monthly.first_seen, EXCLUDED.first_seen),
                    last_seen   = GREATEST(user_song_monthly.last_seen, EXCLUDED.last_seen)
            """;

            default -> throw new IllegalStateException("Unsupported DB vendor: " + v);
        };
    }

    /**
     * 一時削除用
     */
    public void tempDeleteAllByVideoIds(Collection<String> ids) {

        if (ids == null || ids.isEmpty()) return;

        StringBuilder in = new StringBuilder();

        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) in.append(",");
            in.append("?");
        }

        final String sql =
                "DELETE FROM user_song_monthly WHERE video_id IN (" + in + ")";

        try (var con = c(); var ps = con.prepareStatement(sql)) {

            int idx = 1;

            for (String id : ids) {
                ps.setString(idx++, id);
            }

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    
}

