package dto.music;

import domain.youtube.VideoGenre;

public record VideoRecord(
	    String videoId,
	    String channelId,
	    VideoGenre genre,
	    boolean is_confident,
	    String videoTitle,
	    Long musicId,       // nullable
	    long playCount,
	    String thumbnailUrl
	) {}
