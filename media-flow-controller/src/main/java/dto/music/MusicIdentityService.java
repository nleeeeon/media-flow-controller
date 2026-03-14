package dto.music;

import dto.anime.Anime;

public class MusicIdentityService extends MusicSource{
		public String artistKey;
		public String songKey;
		  public MusicIdentityService(String artist,//元のアーティスト名
   				    String song,//元の曲名
					boolean fixed,
					String videoId,
					String channelId,
					Anime anime,
					String artistKey,
					String songKey,
					boolean isPiano,
		  			String videoTitle
				  ) {
						super(song,
						artist,
						fixed,
						videoId,
						anime,
						channelId,
						isPiano,
						videoTitle);

						this.artistKey = artistKey;
						this.songKey = songKey;

		  }
		  
		  public MusicIdentityService(MusicSource m) {
			  super(m);
		  }


		    

	  }
