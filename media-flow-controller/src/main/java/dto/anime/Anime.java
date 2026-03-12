package dto.anime;

public class Anime{
	public String title;
	public int season;
	public int cour;
	public Boolean isOp; 
	public String song;
	public boolean animeFixed;//アニメタイトルと曲名の順序が正しい可能性が高いかどうか。高いならtrue
	public String apiAnimeSong;//APIから取得した曲名を入れる（APIから返る表記がすべて英語で返るため別のエリアを用意）
	public String apiAnimeArtist;//APIから取得したアーティストを入れる（APIから返る表記がすべて英語で返るため別のエリアを用意）
	public Anime(String title, 
		int season, 
		int cour, 
		Boolean isOp, 
		String song,
		boolean animeFixed,
		String apiAnimeSong,
		String apiAnimeArtist) {
		this.title = title; 
		this.season = season;
		this.cour = cour; 
		this.isOp = isOp;
		this.song = song;
		this.animeFixed = animeFixed;
		this.apiAnimeSong = apiAnimeSong;
		this.apiAnimeArtist = apiAnimeArtist;
		
	}
	public Anime() {}
}
