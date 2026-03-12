package infrastructure.index;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;

import dao.music.ArtistDao_SourceIsDBjava;
import dao.music.Music_details_dao;
import domain.youtube.MusicKeyType;
import dto.music.MusicDetail;
import dto.response.UpdateKey;
import infrastructure.index.ArtistSearchIndex.Candidate;
import infrastructure.memorydb.DBJavaVersion;
import util.common.MusicKeyUtils;
import util.string.RomajiConverter;
import util.string.Strings;

public class MusicIndexManager {
	
	
	private Trie<MusicIndexEntry> songKeyPrefix = new Trie<>();
	private ArtistSearchIndex artistSearch;
	private Set<String> ngramIndexedArtists = new HashSet<>();
	private Set<Long> tableDeleteIds = new HashSet<>();
	public DBJavaVersion<Long, MusicDetail> table = new DBJavaVersion<>(row -> row.id());
	
	public Set<Long> getTableDeleteIds(){
		return tableDeleteIds;
	}
	
	public MusicDetail getMusicDetail(long id) {
		MusicDetail m = table.findById(id);
		return m;
	}
	
	public List<MusicDetail> getAllMusicDetail() {
		return table.findAll();
	}
	
	public String getKeyValue(MusicIndexEntry indexKey) {
		  MusicDetail m = table.findById(indexKey.id());
		  String value = indexKey.type() == MusicKeyType.SONG ? m.songKey() : m.artistKey();
		  return value;
	  }
	  
	  public Set<MusicIndexEntry> prefixGet(String q){
		  return songKeyPrefix.get(q);
	  }
	  
	  public MusicDetail musicDetailGet(MusicIndexEntry indexKey) {
		  if(indexKey == null)return null;
		  return table.findById(indexKey.id());
	  }
	  
	  public String musicDetailGetKeyValue(MusicIndexEntry indexKey) {
		  MusicDetail m =  table.findById(indexKey.id());
		  String value = indexKey.type() == MusicKeyType.SONG ? m.songKey() : m.artistKey();
		  return value;
	  }
	  
	  
	  
	  
	  
	  public void addNewArtistNames(String artist, List<String> addArtist) {
		  List<MusicDetail> ms = table.scan("artistKey", artist);
		  for(MusicDetail m : ms) {
			  updateMusic(m.id(), old -> old.withAddArtistKeys(addArtist));
		  }
	  }
	  
	  public void addNewArtistKey(long id, String addArtist) {
		  updateMusic(id, old ->{
			  return old.withAddArtistKey(addArtist);
		  });
	  }
	  
	  public void addNewSongKey(long id, String addSong) {
		  updateMusic(id, old -> {
			  return old.withAddSongKey(addSong);
		  });
	  }
	  
	  public void deleteMusics(Set<Long> ids) {
		  for(long id : ids) {
			  deleteMusic(id, tableDeleteIds);
		  }
	  }
	  
	  public void deleteMusicsByArtist(String artist) {
		  for(MusicDetail m : table.scan("artistKey", artist)) {
			  deleteMusic(m.id(), tableDeleteIds);
		  }
	  }
	  
	  public void renameSongKey(MusicDetail old, String newNameKey) {
		  updateMusicWithIndex(old.id(), m -> {
			  return m.withMainSongKey(newNameKey);
		  });
	  }
	  
	  public void renameKey(long id, UpdateKey newKey) {
		  MusicDetail old = table.findById(id);
		  if(old == null)return;
		  if(old.fixed())return;
		  
		  if(old.artistKey().contains(newKey.oldKey())) {
			  renameArtistKey(old, newKey.nextKey());
		  }else if(old.songKey().contains(newKey.oldKey())) {
			  renameSongKey(old, newKey.nextKey());
		  }
	  }
	  
	  public void renameArtistKey(MusicDetail old, String newNameKey) {
		  updateMusicWithIndex(old.id(), m ->{
			  String artistKey = newNameKey;
			  if(m.fixed()) {//基本的にfixedがtrueの時はアーティスト名は変える処理は来ないはず
			    	Candidate result = artistSearch.matchOne(newNameKey, 10);
			    	if(result != null) {
			    		artistKey = result.artistKey;
			    	}
			    }
			 return m.withMainArtistKey(artistKey);
		  });
		   
		}
	  
	  public void renameArtistAndSong(long id, String newArtistName, String newSongName) {
		  updateMusicWithIndex(id, old -> {
			  String newArtistNameKey = MusicKeyUtils.strongKey(newArtistName);
			    if(old.fixed()) {
			    	Candidate result = artistSearch.matchOne(newArtistNameKey, 10);
			    	if(result != null) {
			    		newArtistNameKey = result.artistKey;
			    	}
			    }
			  MusicDetail updated = old.withArtistAndSongKey(newArtistNameKey, MusicKeyUtils.strongKey(newSongName));
			  updated = updated.withBaseArtistAndSong(newArtistName, newSongName);
			  return updated;
		  });
		  
	  }
	  
	  public void renameArtistAndChangeFixeds(long id, String newName, boolean newFixed) {
		  updateMusicWithIndex(id, old ->{
			  String newArtistNameKey = MusicKeyUtils.strongKey(newName);
			    if(newFixed) {
			    	Candidate result = artistSearch.matchOne(newArtistNameKey, 10);
			    	if(result != null) {
			    		newArtistNameKey = result.artistKey;
			    	}
			    }
			    MusicDetail updated = old.withArtistKey(newArtistNameKey);
			    updated = updated.withBaseArtist(newName);
			    updated = updated.withFixed(newFixed);
			    return updated;
		  });
		
	  }
	  
	  public void renameArtistAndSongAndChangeFixeds(long id, String newArtistName, String newSongName, boolean newFixed) {
		  updateMusicWithIndex(id, old -> {
			  String newArtistNameKey = MusicKeyUtils.strongKey(newArtistName);
			    if(newFixed) {
			    	Candidate result = artistSearch.matchOne(newArtistNameKey, 10);
			    	if(result != null) {
			    		newArtistNameKey = result.artistKey;
			    	}
			    }
			    old = old.withArtistAndSongKey(newArtistNameKey, MusicKeyUtils.strongKey(newSongName));
			    old = old.withBaseArtistAndSong(newArtistName, newSongName);
			    return old.withFixed(newFixed);
		  });
		 
		  
	  }
	  
	  //アーティスト名の削除
	  public void removeArtists(Set<Long> ids) {
		  for(long id : ids) {
			  updateMusicWithIndex(id, old -> {
				  old = old.withArtistKey(null);
				  old = old.withBaseArtist(null);
				  return old.withFixed(true);
			  });
			  
		  }
		  
	  }
	  
	  public void changeFixedsTrue(Set<Long> ids, String artistKey, String artist) {
		  for(long id : ids) {
			  updateMusicWithIndex(id, old -> old.withFixed(true));
		  }
		  
	  }
	  
	  public void swapName(long id) {
		  updateMusicWithIndex(id, old -> old.swapName());
	  }
	  
	  public void swapNameAndFixedTrue(long id) {
		  updateMusicWithIndex(id, old -> {
			  old = old.swapName();
			  return old.withFixed(true);
		  });
	  }
	  
	  public void updateMusicWithIndex(long id, Function<MusicDetail, MusicDetail> updater) {

		    MusicDetail old = table.findById(id);
		    if (old == null) return;

		    removeIndex(old);

		    MusicDetail updated = updater.apply(old);

		    table.upsert(updated);

		    addIndex(updated);
		    
		    if(updated.fixed() && ngramIndexedArtists.add(updated.artistKey())) {
		    	inputArtistSearch(updated.artistKey(), updated.artist());
		    }
		}
	  
	  public void updateMusic(
			  long id,
		        Function<MusicDetail, MusicDetail> updater) {

		    MusicDetail old = table.findById(id);
		    if (old == null) return;

		    MusicDetail updated = updater.apply(old);

		    table.upsert(updated);
		    
		}
	  public void removeIndex(MusicDetail m) {

		    songKeyPrefix.removeValue(m.songKey(),
		        new MusicIndexEntry(m.id(), MusicKeyType.SONG));

		    if (!m.fixed()) {
		        songKeyPrefix.removeValue(m.artistKey(),
		            new MusicIndexEntry(m.id(), MusicKeyType.ARTIST));
		    }
		}
	  
	  public void addIndex(MusicDetail m) {

		    songKeyPrefix.insert(m.songKey(),
		        new MusicIndexEntry(m.id(), MusicKeyType.SONG));

		    if (!m.fixed()) {
		        songKeyPrefix.insert(m.artistKey(),
		            new MusicIndexEntry(m.id(), MusicKeyType.ARTIST));
		    }
		}
	  
	  public void deleteMusic(long id, Set<Long> tableDeleteIds) {
		  MusicDetail m = table.deleteById(id);
		  songKeyPrefix.removeValue(m.songKey(), new MusicIndexEntry(id, MusicKeyType.SONG));
		  if(!m.fixed()) {
			  songKeyPrefix.removeValue(m.artistKey(), new MusicIndexEntry(id, MusicKeyType.ARTIST));
		  }
		  tableDeleteIds.add(m.id());
	  }
	  
	  public void musicInsert(MusicDetail newMusic, boolean anotherMusicFlag) {
		  table.upsert(newMusic);
		  addIndex(newMusic);
		    if (newMusic.fixed() && !anotherMusicFlag) {
		        inputArtistSearch(newMusic.artistKey(), newMusic.artist());
		    }
	  }
	  
	  public Set<Long> findIdsByArtist(String artsitKey){
		  Set<MusicIndexEntry> set = songKeyPrefix.get(artsitKey);/*変更箇所です*/
		  Set<Long> target = new HashSet<>();
		  for(MusicIndexEntry key : set) {
			  MusicDetail keyM = musicDetailGet(key);
			  if(keyM.artistKeys().contains(artsitKey)) {
				  target.add(key.id());
			  }
		  }
		  return target;
	  }
	  
	  public long falsePrefixRecordArtistNum(String recArtistKey){/*変更箇所です*/
		  Set<MusicIndexEntry> list = songKeyPrefix.get(recArtistKey);
		  Set<String> num = new HashSet<>();
		  for(MusicIndexEntry k : list) {
			  MusicDetail kM = musicDetailGet(k);
			  if(getKeyValue(k).equals(recArtistKey) && kM.artistKeys().contains(recArtistKey) && !kM.fixed()) {
				  num.add(kM.songKey());
			  }
		  }
		  
		  return num.size();
	  }
	  
	  public Candidate searchArtistByNgram(String key) {
		  if(key == null)return null;
		  return artistSearch.matchOne(key, 10);
	  }
	  
	  private void inputArtistSearch(String entArtistKey, String artist) {
		  String normArtist = MusicKeyUtils.normKey(artist);
			artistSearch.add(new ArtistSearchIndex.Artist(entArtistKey,normArtist,Strings.isJapanese(normArtist) ? new ArrayList<>(List.of(RomajiConverter.toRomaji(normArtist))) : null));
	  }
	  
	  public List<String> artistSearchAddArtist(String recArtistKey, String deleteArtistKey) {
		  List<String> newArtists = artistSearch.listNames(deleteArtistKey);
		  artistSearch.addAliases(recArtistKey, newArtists);
		  artistSearch.deleteByArtistId(deleteArtistKey);
		  return newArtists;
	  }
	  
	  public void newArtistNameAdd(String artistKey, String artist) {
		  artistSearch.addAlias(artistKey, MusicKeyUtils.normKey(artist));
	  }
	  
	  
	  
	  public void uploadDatabase() throws SQLException {
			//時間を計測。デバッグ用
			    long start = System.nanoTime();
			  {
				  List<String> delete = new ArrayList<>();
				  Map<String, List<String>> insert = new HashMap<>();
				  ArtistDao_SourceIsDBjava dao = new ArtistDao_SourceIsDBjava();
				  Map<String, List<String>> map = dao.allFindAsMap();
				  //dao.deleteAll();//今はとりあえず全消去、全ぶっぱ
				  for(Entry<String, List<String>> e : artistSearch.dumpAll().entrySet()) {
					  String key = e.getKey();
					    List<String> curList = e.getValue();
					    List<String> orgList = map.get(key);

					    if (orgList == null) {
					        // DB にない → INSERT
					        insert.put(e.getKey(), e.getValue());
					    } else if (!curList.equals(orgList)) {
					        // DB にあるが中身が違う → INSERT
					    	delete.add(key);
					    	insert.put(e.getKey(), e.getValue());
					    }
				  }
				  dao.deleteParentByArtistKeys(delete);
				  
				  dao.upsert(insert);
				  long elapsedNanos = System.nanoTime() - start;
			    	double millis = elapsedNanos / 1_000_000.0;
			    	System.out.printf("elapsed: %.3f ms%n", millis);
			    	start = System.nanoTime();
			  }
			  
			  
			  
			  
			  {

				  Music_details_dao dao = new Music_details_dao();
				  List<MusicDetail> musicDetails = new ArrayList<>();

				  for (MusicDetail m : table.findAll()) {
				      
				       musicDetails.add(m);
				  }

				  // 一括保存
				  dao.upsert(musicDetails);  
				  dao.deleteByIds(tableDeleteIds);
				  long elapsedNanos = System.nanoTime() - start;
			    	double millis = elapsedNanos / 1_000_000.0;
			    	System.out.printf("elapsed: %.3f ms%n", millis);
			    	start = System.nanoTime();
			  }
			  
			  
		  }
		  public void init() throws SQLException {
			  //この中にデータベースからアーティストのデータを入れる処理を書く
			  {
				  ArtistDao_SourceIsDBjava dao = new ArtistDao_SourceIsDBjava();
				  ArtistSearchIndex.Builder builder = new ArtistSearchIndex.Builder();
				  List<ArtistSearchIndex.Artist> list = dao.allFind();
				  for(ArtistSearchIndex.Artist a : list) {
					  builder.add(a);
					  ngramIndexedArtists.add(a.id);
				  }
				  artistSearch = builder.build();
			  }
			  //ここに曲名アーティストの検索のデータベースをjava内で生成する
			  table.addUniqueIndex("id", row -> row.id());
			  table.addNonUniqueIndex("artistKey", row -> row.artistKey());
			  {
				  Music_details_dao dao = new Music_details_dao();
				  List<MusicDetail> list = dao.allFind();
				  for(MusicDetail m : list) {
					  		musicInsert(m, true);
				 }
			  }
		  }
		  
		  public void initDebug() {
			  //この中にデータベースからアーティストのデータを入れる処理を書く
			  artistSearch = new ArtistSearchIndex.Builder().build();
			  //ここに曲名アーティストの検索のデータベースをjava内で生成する
			  table.addUniqueIndex("id", row -> row.id());
			  table.addNonUniqueIndex("artistKey", row -> row.artistKey());
		  }
		  
		  
		  
		  public void debugPrint() {
			  for (MusicDetail m : table.findAll()) {


			        System.out.println("====================================");

			        // 基本情報
			        System.out.println("fixed : " + m.fixed());
			        System.out.println("artistKey : " + m.artistKey());
			        System.out.println("songKey : " + m.songKey());

			        System.out.println("====================================");
			    }
	  
		  }

}