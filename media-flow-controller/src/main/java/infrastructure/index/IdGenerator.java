package infrastructure.index;

import java.sql.SQLException;

import dao.music.Music_details_dao;

public final class IdGenerator {
    private long nextId;
    
    public IdGenerator() {
    	
    }
    
    public void init() throws SQLException {
    	Music_details_dao dao = new Music_details_dao();
		long id = dao.findMaxMusicId();
		nextId = id+1;
    }
    
    public IdGenerator(long startId) {
    	this.nextId = startId;
    }

    public long newId() {
        return nextId++;
    }
}