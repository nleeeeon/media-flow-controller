package dto.music;

import dto.anime.Anime;

public class MusicSource{
	public String song;
	public String artist;
	public boolean fixed;
	public String videoId;
	public Anime anime;
	public String channelId;
	public boolean isPiano;
	public String videoTitle;
	public MusicSource(String song,
			String artist,
			boolean fixed,
			String videoId,
			Anime anime,
			String channelId,
			boolean isPiano,
			String videoTitle) {
				this.song = song;
				this.artist = artist;
				this.fixed = fixed;
				this.videoId = videoId;
				this.anime = anime;
				this.channelId = channelId;
				this.isPiano = isPiano;
				this.videoTitle = videoTitle;
	}
	public MusicSource(MusicSource m) {
		this(m.song, m.artist, m.fixed, m.videoId, m.anime, m.channelId, m.isPiano, m.videoTitle);
	}
	
}
