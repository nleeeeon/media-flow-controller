package service.identity;

import dto.music.artistIdentitys;
import listener.AppStartupListener;
import service.music.VideoTitleCorrespondingFinding;
import util.common.MusicKeyUtils;

public final class ArtistIdentityUpdater {

    public void applySameArtist(String newArtist, String oldArtist, int newId, int oldId) {
        VideoTitleCorrespondingFinding fx = new VideoTitleCorrespondingFinding(AppStartupListener.musicIndexManager, AppStartupListener.idGenerator);
        try {
			VideoTitleCorrespondingFinding.withExclusive(() -> {
			    fx.artistSameIdentity(
			        new artistIdentitys(
			        	MusicKeyUtils.strongKey(newArtist),
			        	MusicKeyUtils.strongKey(oldArtist),
			            newArtist, newId, oldId
			        )
			    );
			});
		} catch (InterruptedException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}
    }
}