package dto.anime;

public record AnimeWithVideoId(
		Anime anime,
		String videoId,
		String channelId
		) {}