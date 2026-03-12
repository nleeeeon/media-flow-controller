package service.music;

import java.util.List;

import dao.user.MusicArchiveDao;
import dto.music.MonthlySongStats;

public final class MusicArchiveService {

    private final MusicArchiveDao dao;

    public MusicArchiveService(MusicArchiveDao dao) {
        this.dao = dao;
    }

    public void rollupAndUpsert(long userId, List<MusicClassificationService.Result> recs) {
        List<MonthlySongStats> stats = UserSongMonthlyRollup.rollup(recs);

        dao.upsertItemsBulk(userId, stats);
    }
}