package dto.music;

import util.string.Strings;

public
class Track{
	public String artist = null;
	public String song = null;
	public Track() {}
	public boolean isNull() {
		return Strings.isTrivial(artist) && Strings.isTrivial(song);
	}
	public boolean hasAllValues() {
		return !Strings.isTrivial(artist) && !Strings.isTrivial(song);
	}
	public boolean hasArtist() {
		return !Strings.isTrivial(artist);
	}
	public boolean hasSong() {
		return !Strings.isTrivial(song);
	}
	public boolean isArtistMissing(){
		return Strings.isTrivial(artist) && !Strings.isTrivial(song);
	}
	public boolean isSongMissing(){
		return !Strings.isTrivial(artist) && Strings.isTrivial(song);
	}
	public void clear() {
		artist = null;
		song = null;
	}
	public Track copy() {
		return new Track(artist, song);
	}
	public Track(String artist, String song) {
		this.artist = artist;
		this.song = song;
	}
	public void swapNamesIfNeeded() {
		if(isSongMissing()) {
			song = artist;
			artist = null;
		}
	}
}