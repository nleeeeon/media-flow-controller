package dto.music;

import java.util.ArrayList;
import java.util.List;

public record MusicDetail(
		long id,//データベース用に
		  List<String> artistKeys,//嵐、ARASHIなどわかれたりするのでリスト
		  List<String> songKeys,//曲名も念のために複数表記できるように
		  int artistMainIndex,//主に使われるアーティストの要素番号
		  int songMainIndex,//主に使われる曲名の要素番号
		  boolean fixed,//keyValueが曲名の可能性が高ければtrue。falseの場合は、同じkeyValueで複数が登録されてることもある
		  long playCount,
		  String artist,
		  String song
) {
	public MusicDetail(DeterminationMusic m, long id, long playCount) {
		
		this(id, List.of(m.artistKey), List.of(m.songKey), 0, 0, m.fixed, playCount, m.artist, m.song);
	}
	public String artistKey() {
		return this.artistKeys.get(artistMainIndex);
	}
	
	public String songKey() {
		return this.songKeys.get(songMainIndex);
	}
	
	public MusicDetail(MusicDetail m) {
		this(m.id(),m.artistKeys,m.songKeys,m.artistMainIndex(),m.songMainIndex,m.fixed,m.playCount,m.artist,m.song);
	}
	
	public MusicDetail withAddArtistKeys(List<String> addArtistKeys) {
		List<String> newArtistKeys = new ArrayList<>(artistKeys);
		newArtistKeys.addAll(addArtistKeys);
		return new MusicDetail(
                id,
                newArtistKeys,
                songKeys,
                artistMainIndex,
                songMainIndex,
                fixed,
                playCount,
                artist,
                song
        );
	}
	public MusicDetail withArtistKeys(List<String> newArtistKeys) {
        return new MusicDetail(
                id,
                newArtistKeys,
                songKeys,
                artistMainIndex,
                songMainIndex,
                fixed,
                playCount,
                artist,
                song
        );
    }

    public MusicDetail withSongKeys(List<String> newSongKeys) {
        return new MusicDetail(
                id,
                artistKeys,
                newSongKeys,
                artistMainIndex,
                songMainIndex,
                fixed,
                playCount,
                artist,
                song
        );
    }
    
    public MusicDetail withArtistKey(String newArtistKey) {
        return new MusicDetail(
                id,
                newArtistKey == null ? List.of("") : List.of(newArtistKey),
                songKeys,
                0,
                songMainIndex,
                fixed,
                playCount,
                artist,
                song
        );
    }
    
    public MusicDetail withBaseArtist(String newBaseArtist) {
        return new MusicDetail(
                id,
                artistKeys,
                songKeys,
                artistMainIndex,
                songMainIndex,
                fixed,
                playCount,
                newBaseArtist,
                song
        );
    }
    
    public MusicDetail withSongKey(String newSongKey) {
        return new MusicDetail(
                id,
                artistKeys,
                newSongKey == null ? List.of("") :List.of(newSongKey),
                artistMainIndex,
                0,
                fixed,
                playCount,
                artist,
                song
        );
    }
    
    public MusicDetail withMainSongKey(String newSongKey) {
    	List<String> newSongKeys = new ArrayList<>(songKeys);
    	newSongKeys.add(newSongKey);
        return new MusicDetail(
                id,
                artistKeys,
                newSongKeys,
                artistMainIndex,
                songKeys.size()-1,
                fixed,
                playCount,
                artist,
                song
        );
    }
    
    public MusicDetail withAddSongKey(String newSongKey) {
    	List<String> newSongKeys = new ArrayList<>(songKeys);
    	newSongKeys.add(newSongKey);
        return new MusicDetail(
                id,
                artistKeys,
                newSongKeys,
                artistMainIndex,
                songMainIndex,
                fixed,
                playCount,
                artist,
                song
        );
    }
    
    public MusicDetail withMainArtistKey(String newArtistKey) {
    	List<String> newArtistKeys = new ArrayList<>(artistKeys);
    	newArtistKeys.add(newArtistKey);
        return new MusicDetail(
                id,
                newArtistKeys,
                songKeys,
                artistKeys.size()-1,
                songMainIndex,
                fixed,
                playCount,
                artist,
                song
        );
    }
    
    public MusicDetail withAddArtistKey(String newArtistKey) {
    	List<String> newArtistKeys = new ArrayList<>(artistKeys);
    	newArtistKeys.add(newArtistKey);
        return new MusicDetail(
                id,
                newArtistKeys,
                songKeys,
                artistMainIndex,
                songMainIndex,
                fixed,
                playCount,
                artist,
                song
        );
    }
    
    public MusicDetail withBaseSong(String newBaseSong) {
        return new MusicDetail(
                id,
                artistKeys,
                songKeys,
                artistMainIndex,
                songMainIndex,
                fixed,
                playCount,
                artist,
                newBaseSong
        );
    }
    
    public MusicDetail withArtistAndSongKey(String newArtistKey, String newSongKey) {
    	return new MusicDetail(
                id,
                List.of(newArtistKey),
                List.of(newSongKey),
                0,
                0,
                fixed,
                playCount,
                artist,
                song
        );
    }
    
    public MusicDetail withBaseArtistAndSong(String newBaseArtist, String newBaseSong) {
        return new MusicDetail(
                id,
                artistKeys,
                songKeys,
                artistMainIndex,
                songMainIndex,
                fixed,
                playCount,
                newBaseArtist,
                newBaseSong
        );
    }
    
    public MusicDetail swapName() {
    	return new MusicDetail(
                id,
                songKeys,
                artistKeys,
                songMainIndex,
                artistMainIndex,
                fixed,
                playCount,
                song,
                artist
        );
    }
    
    public MusicDetail withFixed(boolean newFixed) {
        return new MusicDetail(
                id,
                artistKeys,
                songKeys,
                artistMainIndex,
                songMainIndex,
                newFixed,
                playCount,
                artist,
                song
        );
    }
    
}