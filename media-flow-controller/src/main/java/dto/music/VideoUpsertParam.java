package dto.music;

import domain.youtube.VideoGenre;

public record VideoUpsertParam(
	    String videoId,
	    String channelId,
	    VideoGenre genre,
	    boolean is_confident,
	    String videoTitle,
	    long playCount,
	    String thumbnailUrl
	) {}
