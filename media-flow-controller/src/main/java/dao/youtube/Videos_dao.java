package dao.youtube;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.JsonObject;

import domain.youtube.VideoGenre;
import dto.music.VideoRecord;
import dto.music.VideoUpsertParam;
import dto.youtube.VideoInput;
import infrastructure.db.Db;
import infrastructure.db.DbVendorChecker;
import infrastructure.db.DbVendorChecker.DbVendor;
import service.music.MusicClassificationService;

public class Videos_dao {
	private Connection c() throws SQLException {
		return Db.get();
	}
	private static final DbVendor VENDOR = DbVendorChecker.get();
	public void upsertInitial(
	        List<VideoInput> rows,
	        Map<String, String> thumbnailMap,
	        Map<String, Long> playCountMap,
	        Map<String, JsonObject> jsonMap
	) throws SQLException {

	    if (rows == null || rows.isEmpty()) return;

	    final String sql;

	    if (VENDOR == DbVendor.POSTGRES) {
	        sql = """
	            INSERT INTO videos (
	              video_id, channel_id, video_title,
	              play_count, thumbnail_url
	            ) VALUES (?,?,?,?,?)
	            ON CONFLICT (video_id) DO UPDATE SET
	              channel_id    = EXCLUDED.channel_id,
	              video_title   = EXCLUDED.video_title,
	              play_count    = EXCLUDED.play_count,
	              thumbnail_url = EXCLUDED.thumbnail_url
	            """;
	    } else {
	        sql = """
	            INSERT INTO videos (
	              video_id, channel_id, video_title,
	              play_count, thumbnail_url
	            ) VALUES (?,?,?,?,?)
	            ON DUPLICATE KEY UPDATE
	              channel_id=VALUES(channel_id),
	              video_title=VALUES(video_title),
	              play_count=VALUES(play_count),
	              thumbnail_url=VALUES(thumbnail_url)
	            """;
	    }

	    try (var con = c(); var ps = con.prepareStatement(sql)) {

	        int count = 0;

	        for (VideoInput w : rows) {

	            String videoId = w.videoId;
	            JsonObject json = jsonMap.get(videoId);

	            String channelId = null;
	            if (json != null) {
	                channelId = json
	                        .getAsJsonObject("snippet")
	                        .get("channelId")
	                        .getAsString();
	            }

	            ps.setString(1, videoId);
	            ps.setString(2, channelId);
	            ps.setString(3, w.title);
	            ps.setLong(4, playCountMap.getOrDefault(videoId, 0L));
	            ps.setString(5, thumbnailMap.get(videoId));

	            ps.addBatch();

	            if (++count % 500 == 0) {
	                ps.executeBatch();
	            }
	        }

	        ps.executeBatch();
	    }
	}
	
	public void upsertInitial(List<VideoUpsertParam> rows) throws SQLException {

	    if (rows == null || rows.isEmpty()) return;

	    final String sql;

	    if (VENDOR == DbVendor.POSTGRES) {
	        sql = """
	            INSERT INTO videos (
	              video_id,
	              channel_id,
	              genre,
	              is_confident,
	              video_title,
	              play_count,
	              thumbnail_url
	            ) VALUES (?,?,?,?,?,?,?)
	            ON CONFLICT (video_id) DO UPDATE SET
	              channel_id    = EXCLUDED.channel_id,
	              genre         = EXCLUDED.genre,
	              is_confident  = EXCLUDED.is_confident,
	              video_title   = EXCLUDED.video_title,
	              play_count    = EXCLUDED.play_count,
	              thumbnail_url = EXCLUDED.thumbnail_url
	            """;
	    } else {
	        sql = """
	            INSERT INTO videos (
	              video_id,
	              channel_id,
	              genre,
	              is_confident,
	              video_title,
	              play_count,
	              thumbnail_url
	            ) VALUES (?,?,?,?,?,?,?)
	            ON DUPLICATE KEY UPDATE
	              channel_id=VALUES(channel_id),
	              genre=VALUES(genre),
	              is_confident=VALUES(is_confident),
	              video_title=VALUES(video_title),
	              play_count=VALUES(play_count),
	              thumbnail_url=VALUES(thumbnail_url)
	            """;
	    }

	    try (var con = c(); var ps = con.prepareStatement(sql)) {

	        int count = 0;

	        for (VideoUpsertParam row : rows) {

	            ps.setString(1, row.videoId());
	            ps.setString(2, row.channelId());

	            if (row.genre() != null) {
	                ps.setString(3, row.genre().name());
	            } else {
	                ps.setNull(3, java.sql.Types.VARCHAR);
	            }

	            ps.setInt(4, row.is_confident() ? 1 : 0);

	            ps.setString(5, row.videoTitle());
	            ps.setLong(6, row.playCount());
	            ps.setString(7, row.thumbnailUrl());

	            ps.addBatch();

	            if (++count % 500 == 0) {
	                ps.executeBatch();
	            }
	        }

	        ps.executeBatch();
	    }
	}
	
	public void updateGenreBatch(List<MusicClassificationService.Result> reqs) throws SQLException {

	    if (reqs == null || reqs.isEmpty()) return;

	    String sql = """
	        UPDATE videos
	        SET genre = ?, is_confident = ?
	        WHERE video_id = ?
	        """;

	    try (var con = c();
	         var ps = con.prepareStatement(sql)) {

	        int count = 0;

	        for (MusicClassificationService.Result r : reqs) {

	            if (r.category() != null) {
	                ps.setString(1, r.category().name());
	            } else {
	                ps.setNull(1, java.sql.Types.VARCHAR);
	            }

	            ps.setBoolean(2, r.is_confident());
	            ps.setString(3, r.videoId());

	            ps.addBatch();

	            if (++count % 500 == 0) {
	                ps.executeBatch();
	            }
	        }

	        ps.executeBatch();
	    }
	}
	public void updateMusicIdBatch(Map<String, Long> musicIdMap) throws SQLException {

	    if (musicIdMap == null || musicIdMap.isEmpty()) return;

	    String sql = "UPDATE videos SET music_id = ? WHERE video_id = ?";

	    try (var con = c(); var ps = con.prepareStatement(sql)) {

	        int count = 0;

	        for (var e : musicIdMap.entrySet()) {

	            ps.setLong(1, e.getValue());
	            ps.setString(2, e.getKey());

	            ps.addBatch();

	            if (++count % 500 == 0) {
	                ps.executeBatch();
	            }
	        }

	        ps.executeBatch();
	    }
	}
	
	public VideoRecord findByVideoId(String videoId) throws SQLException {

	    final String sql = """
	        SELECT
	          video_id,
	          channel_id,
	          genre,
	          is_confident,
	          video_title,
	          music_id,
	          play_count,
	          thumbnail_url
	        FROM videos
	        WHERE video_id = ?
	    """;

	    try (var con = c();
	         var ps = con.prepareStatement(sql)) {

	        ps.setString(1, videoId);

	        try (var rs = ps.executeQuery()) {

	            if (!rs.next()) {
	                return null;
	            }

	            Long musicId = rs.getObject("music_id") == null
	                    ? null
	                    : rs.getLong("music_id");

	            VideoGenre genre = null;
	            String genreStr = rs.getString("genre");
	            if (genreStr != null) {
	                genre = VideoGenre.valueOf(genreStr);
	            }

	            return new VideoRecord(
	                    rs.getString("video_id"),
	                    rs.getString("channel_id"),
	                    genre,
	                    rs.getBoolean("is_confident"),
	                    rs.getString("video_title"),
	                    musicId,
	                    rs.getLong("play_count"),
	                    rs.getString("thumbnail_url")
	            );
	        }
	    }
	}
	
	public Map<String, VideoRecord> findByVideoIds(Collection<String> videoIds) throws SQLException {

	    if (videoIds == null || videoIds.isEmpty()) {
	        return Map.of();
	    }

	    String placeholders = videoIds.stream()
	            .map(v -> "?")
	            .collect(Collectors.joining(","));

	    final String sql = """
	        SELECT
	          video_id,
	          channel_id,
	          genre,
	          is_confident,
	          video_title,
	          music_id,
	          play_count,
	          thumbnail_url
	        FROM videos
	        WHERE video_id IN (%s)
	    """.formatted(placeholders);

	    Map<String, VideoRecord> result = new HashMap<>();

	    try (var con = c();
	         var ps = con.prepareStatement(sql)) {

	        int i = 1;
	        for (String id : videoIds) {
	            ps.setString(i++, id);
	        }

	        try (var rs = ps.executeQuery()) {

	            while (rs.next()) {

	                Long musicId = rs.getObject("music_id") == null
	                        ? null
	                        : rs.getLong("music_id");

	                VideoGenre genre = null;
	                String genreStr = rs.getString("genre");
	                if (genreStr != null) {
	                    genre = VideoGenre.valueOf(genreStr);
	                }

	                VideoRecord record = new VideoRecord(
	                        rs.getString("video_id"),
	                        rs.getString("channel_id"),
	                        genre,
	                        rs.getBoolean("is_confident"),
	                        rs.getString("video_title"),
	                        musicId,
	                        rs.getLong("play_count"),
	                        rs.getString("thumbnail_url")
	                );

	                result.put(record.videoId(), record);
	            }
	        }
	    }

	    return result;
	}
	
}

