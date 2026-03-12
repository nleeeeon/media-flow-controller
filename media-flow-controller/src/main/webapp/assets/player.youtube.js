/* ---- IFrame API Ready ---- */
window.onYouTubeIframeAPIReady = function(){
  // プレイヤーの入れ物は id="player" を想定
  player = new YT.Player("player", {
    width: "100%",
    height: "390",
    playerVars: {
      rel: 0,
      modestbranding: 1,
      playsinline: 1
    },
    events: {
      onReady: onPlayerReady,
      onError: onPlayerError,
      onStateChange: onPlayerStateChange
    }
  });
};

function onPlayerReady(){
  PLAYER_READY = true;
  bindSkipUI();
  renderSkipUI();
  // すでに待機しているデータがあれば読み込む
  if (PLAYLIST_ID) {
    try { player.loadPlaylist({ listType: "playlist", list: PLAYLIST_ID }); } catch(_){}
    //justLoadedPlaylist = true;
    //justLoadedPlaylistId = PLAYLIST_ID;
    //justLoadedVideoSlice = null;
  } else if (Array.isArray(PLAYLIST) && PLAYLIST.length){
    try { player.loadPlaylist(PLAYLIST, 0, 0, "large"); } catch(_){}
    //justLoadedPlaylist = true;
    //justLoadedPlaylistId = null;
    //justLoadedVideoSlice = PLAYLIST;
  }
}

/* ---- エラーハンドリング：埋め込み不可や地域制限は次へ ---- */
function onPlayerError(e){
  const code = e?.data;
  console.warn("[player-error]", code);
  // 代表的なエラーコード：2,5,100,101,150
  if ([2,5,100,101,150].includes(code)) {
    try { player.nextVideo(); } catch(_){}
  }
}
let LAST_SETUP_VID = null;//同じ動画で何回もsetupForCurrentVideo関数が呼ばれないようにするため


function onPlayerStateChange(e){
  const s = e.data;
  console.log(s + "これがstateChange");
  const vid = getVideoId();
  // ★ 同じ動画での二重セットアップは抑止
  const shouldSetup = !!vid && vid !== LAST_SETUP_VID;

  // ★ UNSTARTED(-1) のとき：開始位置付きでプレイリストを再ロード
  if (s === YT.PlayerState.UNSTARTED && vid) {
	  
    // 直前に自分で loadPlaylist した結果の -1 なら何もしないでフラグだけ戻す
    if (RELOAD_FOR_START) {
      RELOAD_FOR_START = false;
      return;
    }

    // 動画が切り替わったらフラグをリセット
    if (START_APPLIED_VID && START_APPLIED_VID !== vid) {
      START_APPLIED_VID = null;
    }

    // この動画に対してまだ開始位置適用をしていないなら実行
    if (!START_APPLIED_VID) {
      applyStartFromPlaylistOnUnstarted(vid, false, -1);  // 非同期でOK
    	if(!changePianoVideo){
			pianoChanged = false;
			loadPianoCandidatesStop = false;
		}
    }
    changePianoVideo = false;
    // UNSTARTED のときはここで return しておく（PLAYING 分岐に入らない）
    return;
  }

  

  if (s === YT.PlayerState.PLAYING && shouldSetup) {
	  updatePianoHighlight(vid);
    updateNowPlayingStats(vid);
    console.log("[DEBUG] Now playing videoId:", vid);

    setupForCurrentVideo();
    startChecking();
    prefetchNextSegments();

    if (location.hash === "#/segments") {
      loadUserSegs(vid);
    }
    showPianoChangeButton();
    // ★★★ ここから追記（ピアノ候補の更新） ★★★
    const details = document.getElementById("pianoSuggestDetails");
    const card    = document.getElementById("pianoSuggestCard");
    if (details && card) {
      // いったん候補をクリアして非表示にする
      if(!loadPianoCandidatesStop)renderPianoSuggestions(null, null);
      card.dataset.loadedVideo = "";

      // details が開きっぱなしなら、新しい動画の候補を即ロード
      if (details.open && !loadPianoCandidatesStop) {
        loadPianoCandidatesForCurrentVideo()
          .then(() => { card.dataset.loadedVideo = vid; })
          .catch(err => console.warn("piano candidates reload failed:", err));
      }
      
      /*if(loadPianoCandidatesStop){
		  loadPianoCandidatesStop = false;
	  }*/
    }
    // ★★★ ここまで追記 ★★★
    const plDetails = document.getElementById("playlistDetails");
    const plCard    = document.getElementById("playlistCard");

    if (plDetails && plCard) {
      if (justLoadedPlaylist) {
        // 「このプレイリストを最初に読み込んだときの再生」だけ
        justLoadedPlaylist = false;  // 一度使ったら下ろす

        // パネルが開いているときだけロードしたい場合
        if (plDetails.open) {
          loadCurrentPlaylistPanel()
            .then(() => {
              // 描画後にハイライトも一応合わせておく
              updatePlaylistHighlight(vid);
            })
            .catch(err => console.warn("playlist panel load failed:", err));
        }
      } else {
        // 2 本目以降の動画への切り替え時はハイライトだけ更新
        updatePlaylistHighlight(vid);
      }
    }


  } else if (s === YT.PlayerState.ENDED) {
    stopChecking();
  }
  
}

let justLoadedPlaylist = false;
let justLoadedPlaylistId = null;
let justLoadedVideoSlice = null;
let changePianoVideo = false;
let pianoChanged = false;
