package dao.music;

import static java.util.stream.Collectors.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import dto.music.MusicDetail;
import infrastructure.db.Db;
import infrastructure.db.DbVendorChecker;
import infrastructure.db.DbVendorChecker.DbVendor;

public class Music_details_dao {

    private Connection c() throws SQLException {
        return Db.get();
    }
    
    public long findMaxMusicId() throws SQLException {

        String sql = "SELECT COALESCE(MAX(music_id), 0) FROM music_details";

        try (var con = c();
             var ps = con.prepareStatement(sql);
             var rs = ps.executeQuery()) {

            rs.next();
            return rs.getLong(1);
        }
    }

    public void deleteAll() throws SQLException {
	    String sql = "DELETE FROM music_details";
	    try (var con = c(); var ps = con.prepareStatement(sql)) {
	        int count = ps.executeUpdate();
	        ///System.out.println("artist テーブルから " + count + " 件を削除しました。");
	    } catch (SQLException e) {
	        e.printStackTrace();
	        throw e;
	    }
	}
    public int deleteByIds(Set<Long> ids) throws SQLException {

        if (ids == null || ids.isEmpty()) return 0;

        String inClause = ids.stream()
                .map(id -> "?")
                .collect(Collectors.joining(","));

        String sql = "DELETE FROM music_details WHERE music_id IN (" + inClause + ")";

        try (var con = c();
             var ps = con.prepareStatement(sql)) {

            int i = 1;
            for (Long id : ids) {
                ps.setLong(i++, id);
            }

            return ps.executeUpdate();
        }
    }
    /* =====================================================
       取得（親→子）
       ===================================================== */
    public List<MusicDetail> allFind() throws SQLException {

        Map<Long, RowBuilder> map = new LinkedHashMap<>();

        String parentSql = """
            SELECT music_id, artist_key, song_key,
                   base_artist, base_song, fixed
            FROM music_details
            ORDER BY music_id
        """;

        try (var con = c();
             var ps = con.prepareStatement(parentSql);
             var rs = ps.executeQuery()) {

            while (rs.next()) {
                long id = rs.getLong("music_id");

                RowBuilder t = new RowBuilder();
                t.id = id;
                t.mainArtist = rs.getString("artist_key");
                t.mainSong   = rs.getString("song_key");
                t.baseArtist = rs.getString("base_artist");
                t.baseSong   = rs.getString("base_song");
                t.fixed      = rs.getBoolean("fixed");

                map.put(id, t);
            }
        }

        if (map.isEmpty()) return List.of();

        // -------- artist_keys --------
        String in = map.keySet().stream()
                .map(x -> "?")
                .collect(joining(","));

        String aSql = "SELECT music_id, artist_key FROM music_details_artist_keys WHERE music_id IN (" + in + ")";

        try (var con = c();
             var ps = con.prepareStatement(aSql)) {

            int i = 1;
            for (Long id : map.keySet()) ps.setLong(i++, id);

            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("music_id");
                    map.get(id).artistKeys.add(rs.getString("artist_key"));
                }
            }
        }

        // -------- song_keys --------
        String sSql = "SELECT music_id, song_key FROM music_details_song_keys WHERE music_id IN (" + in + ")";

        try (var con = c();
             var ps = con.prepareStatement(sSql)) {

            int i = 1;
            for (Long id : map.keySet()) ps.setLong(i++, id);

            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("music_id");
                    map.get(id).songKeys.add(rs.getString("song_key"));
                }
            }
        }

        // -------- record生成 --------
        List<MusicDetail> result = new ArrayList<>();

        for (RowBuilder t : map.values()) {

            int artistIndex = searchIndex(t.artistKeys, t.mainArtist);
            int songIndex   = searchIndex(t.songKeys, t.mainSong);

            result.add(new MusicDetail(
                    (int) t.id,
                    t.artistKeys,
                    t.songKeys,
                    artistIndex,
                    songIndex,
                    t.fixed,
                    0L, // playCountはテーブルに無いので0
                    t.baseArtist,
                    t.baseSong
            ));
        }

        return result;
    }

    private int searchIndex(List<String> list, String value) {
        for (int i = 0; i < list.size(); i++) {
            if (Objects.equals(list.get(i), value)) return i;
        }
        return 0;
    }

    /* =====================================================
       UPSERT
       ===================================================== */
    public void upsert(List<MusicDetail> list) throws SQLException {

        if (list == null || list.isEmpty()) return;

        DbVendor v = DbVendorChecker.get();

        String parentSql = buildParentSql(v);

        String delArtist = "DELETE FROM music_details_artist_keys WHERE music_id = ?";
        String delSong   = "DELETE FROM music_details_song_keys WHERE music_id = ?";

        String insArtist = "INSERT INTO music_details_artist_keys (music_id, artist_key) VALUES (?,?)";
        String insSong   = "INSERT INTO music_details_song_keys   (music_id, song_key)   VALUES (?,?)";

        try (var con = c()) {

            con.setAutoCommit(false);

            try (var pps = con.prepareStatement(parentSql);
                 var das = con.prepareStatement(delArtist);
                 var dss = con.prepareStatement(delSong);
                 var ias = con.prepareStatement(insArtist);
                 var iss = con.prepareStatement(insSong)) {

                for (MusicDetail m : list) {

                    pps.setLong(1, m.id());
                    pps.setString(2, m.artistKey());
                    pps.setString(3, m.songKey());
                    pps.setString(4, m.artist());
                    pps.setString(5, m.song());
                    pps.setBoolean(6, m.fixed());

                    pps.executeUpdate();

                    long id = m.id();

                    // 子再構築
                    das.setLong(1, id);
                    das.executeUpdate();

                    dss.setLong(1, id);
                    dss.executeUpdate();

                    for (String a : new LinkedHashSet<>(m.artistKeys())) {
                        ias.setLong(1, id);
                        ias.setString(2, a);
                        ias.addBatch();
                    }
                    ias.executeBatch();

                    for (String s : new LinkedHashSet<>(m.songKeys())) {
                        iss.setLong(1, id);
                        iss.setString(2, s);
                        iss.addBatch();
                    }
                    iss.executeBatch();
                }

                con.commit();

            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    private String buildParentSql(DbVendor v) {

        return switch (v) {

            case MARIADB -> """
                INSERT INTO music_details
                (music_id, artist_key, song_key, base_artist, base_song, fixed)
                VALUES (?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                  artist_key = VALUES(artist_key),
                  song_key   = VALUES(song_key),
                  base_artist= VALUES(base_artist),
                  base_song  = VALUES(base_song),
                  fixed      = VALUES(fixed)
                """;

            case POSTGRES -> """
                INSERT INTO music_details
                (music_id, artist_key, song_key, base_artist, base_song, fixed)
                VALUES (?,?,?,?,?,?)
                ON CONFLICT (music_id) DO UPDATE SET
                  artist_key = EXCLUDED.artist_key,
                  song_key   = EXCLUDED.song_key,
                  base_artist= EXCLUDED.base_artist,
                  base_song  = EXCLUDED.base_song,
                  fixed      = EXCLUDED.fixed
                """;

            default -> throw new IllegalStateException("未対応DB");
        };
    }
    private static class RowBuilder {

        long id;

        String mainArtist;
        String mainSong;

        String baseArtist;
        String baseSong;

        boolean fixed;

        List<String> artistKeys = new ArrayList<>();
        List<String> songKeys   = new ArrayList<>();
    }
}
