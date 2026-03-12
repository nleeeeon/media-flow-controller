/* ---- IFrame へ読み込み ---- */
function loadIdsIntoPlayer(ids){
  if (!Array.isArray(ids) || ids.length === 0) {
    setMsg("エラー: 読み込む動画がありません。");
    return;
  }
  if (!PLAYER_READY || !player) {
    PLAYLIST_ID = undefined;
    PLAYLIST = ids.slice();
    setMsg(`プレイヤー準備中…（${PLAYLIST.length}件）`);
    return;
  }
  try {
    PLAYLIST_ID = undefined;
    PLAYLIST = ids.slice();
    LAST_SETUP_VID = null;
    player.stopVideo();//このストップは必要です。これがないと同じプレイリストIDが再度読み込まれる
    //onPlayerStateChange関数と同じようなものを呼び出すため
    setTimeout(() => {
	    //player.loadPlaylist(PLAYLIST, 0, 0, "large");
	    START_APPLIED_VID = null;
	    applyStartFromPlaylistOnUnstarted(PLAYLIST[0], true, -1);
	    justLoadedPlaylist = true;
	    justLoadedPlaylistId = null;
	    justLoadedVideoSlice = PLAYLIST;
      try {
		  updateNowPlayingStats(PLAYLIST[0]);
        setupForCurrentVideo();      // セグメント取得/UI更新
        startChecking();
        prefetchNextSegments();      // 先読み（任意）
        
      } catch (_) {
		stopChecking();
	  }
    }, 0);
  } catch (e) {
    console.error("loadPlaylist failed:", e);
    setMsg("エラー: プレイヤーへの読み込みに失敗しました。");
  }
}

//デバッグ用の関数。使わなくなったら消していいよ
async function judgeDebug() {
  const setMsg = (m)=> (document.getElementById("judgeMsg").textContent = m);
  const outEl  = document.getElementById("judgeOut");
  outEl.textContent = "";

  // 1) 入力のID群を取得（空ならプレイヤーの現在プレイリスト）
  let idsText = (document.getElementById("judgeIds")?.value || "").trim();
  let ids = [];
  if (idsText) {
    ids = idsText.split(/[,\s]+/).filter(Boolean);
  } else if (player && typeof player.getPlaylist === "function") {
    try { ids = (player.getPlaylist() || []).filter(Boolean); } catch(_) {}
  }

  // 2) パラメータ組み立て
  const params = new URLSearchParams();
  if (ids.length) {
    params.set("ids", ids.join(","));
  } else if (typeof PLAYLIST_ID === "string" && PLAYLIST_ID) {
    params.set("playlistId", PLAYLIST_ID);
  } else {
    setMsg("対象がありません。動画IDを入力するか、プレイリストを読み込んでください。");
    return;
  }

  setMsg("判定実行中…（詳細はサーバログを確認）");

  const url = (APP_CTX || "") + "/youtube/judgeDebug?" + params.toString();
  let text = "";
  try {
    const resp = await fetch(url, { method:"GET", cache:"no-store", credentials:"include" });
    text = await resp.text();
  } catch (e) {
    setMsg("エラー: ネットワーク失敗: " + (e?.message || e));
    return;
  }

  let data;
  try { data = JSON.parse(text); }
  catch(e){ setMsg("エラー: JSONが不正です"); return; }

  if (!data?.ok) {
    setMsg("エラー: " + (data?.message || data?.code || "unknown"));
    return;
  }

  // 概要だけ前面に出す（詳細はSystem.outに全部出しています）
  const lines = [];
  for (const row of (data.results || [])) {
    lines.push(
      `${row.videoId}  isMusic=${row.isMusic}  score=${row.score}/${row.threshold}  title=${row.title}`
    );
  }
  outEl.textContent = lines.join("\n");
  setMsg(`完了: ${data.results?.length || 0}件。詳細はサーバログ（System.out）を参照。`);
}

/* ---- サブ: 指定した期間の検索  ---- */
