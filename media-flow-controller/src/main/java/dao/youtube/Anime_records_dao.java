package dao.youtube;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import dto.anime.Anime;
import infrastructure.db.Db;
import infrastructure.db.DbVendorChecker;
import infrastructure.db.DbVendorChecker.DbVendor;

public class Anime_records_dao {
	private Connection c() throws SQLException {
		return Db.get();
	}
	private static final DbVendor VENDOR = DbVendorChecker.get();
	public void upsertAnimeBatch(Map<String, Anime> animeMap) throws SQLException {

	    if (animeMap == null || animeMap.isEmpty()) return;

	    final String sql;

	    if (VENDOR == DbVendor.POSTGRES) {
	        sql = """
	            INSERT INTO anime_records (
	              video_id,
	              anime_title,
	              anime_season,
	              anime_cour,
	              anime_is_op,
	              anime_song,
	              anime_fixed,
	              anime_api_song,
	              anime_api_artist
	            ) VALUES (?,?,?,?,?,?,?,?,?)
	            ON CONFLICT (video_id) DO UPDATE SET
	              anime_title      = EXCLUDED.anime_title,
	              anime_season     = EXCLUDED.anime_season,
	              anime_cour       = EXCLUDED.anime_cour,
	              anime_is_op      = EXCLUDED.anime_is_op,
	              anime_song       = EXCLUDED.anime_song,
	              anime_fixed      = EXCLUDED.anime_fixed,
	              anime_api_song   = EXCLUDED.anime_api_song,
	              anime_api_artist = EXCLUDED.anime_api_artist
	            """;
	    } else {
	        sql = """
	            INSERT INTO anime_records (
	              video_id,
	              anime_title,
	              anime_season,
	              anime_cour,
	              anime_is_op,
	              anime_song,
	              anime_fixed,
	              anime_api_song,
	              anime_api_artist
	            ) VALUES (?,?,?,?,?,?,?,?,?)
	            ON DUPLICATE KEY UPDATE
	              anime_title=VALUES(anime_title),
	              anime_season=VALUES(anime_season),
	              anime_cour=VALUES(anime_cour),
	              anime_is_op=VALUES(anime_is_op),
	              anime_song=VALUES(anime_song),
	              anime_fixed=VALUES(anime_fixed),
	              anime_api_song=VALUES(anime_api_song),
	              anime_api_artist=VALUES(anime_api_artist)
	            """;
	    }

	    try (var con = c();
	         var ps = con.prepareStatement(sql)) {

	        int count = 0;

	        for (var entry : animeMap.entrySet()) {

	            String videoId = entry.getKey();
	            Anime a = entry.getValue();

	            int i = 1;

	            ps.setString(i++, videoId);
	            ps.setString(i++, a != null ? a.title : null);
	            ps.setObject(i++, a != null ? a.season : null, java.sql.Types.INTEGER);
	            ps.setObject(i++, a != null ? a.cour : null, java.sql.Types.INTEGER);
	            ps.setBoolean(i++, a != null && Boolean.TRUE.equals(a.isOp));
	            ps.setString(i++, a != null ? a.song : null);
	            ps.setBoolean(i++, a != null && a.animeFixed);
	            ps.setString(i++, a != null ? a.apiAnimeSong : null);
	            ps.setString(i++, a != null ? a.apiAnimeArtist : null);

	            ps.addBatch();

	            if (++count % 500 == 0) {
	                ps.executeBatch();
	            }
	        }

	        ps.executeBatch();
	    }
	}
}
