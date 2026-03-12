/* ---- 音楽対応するピアノ動画を引っ張ってくるコードです ---- */
window.getPianoVideo = getPianoVideo;
async function getPianoVideo() {
  const gate = document.getElementById("pianoChangePanel");
  if (gate) gate.style.display = "none";

  

  const videoId = getVideoId();


  showToast("ピアノ動画を探しています…");

  const url = (APP_CTX || "") + "/piano/getVideo?videoId=" + videoId;
  let data = await servletResult(url);

  // ▼ thumbnails の描画（pianoVideoId とセットで渡す） ----------
  if (data && data.thumbnails && !data.recordPianoVideo) {
    try {
      renderPianoSuggestions(data.thumbnails, data.pianoVideoId);
      
    } catch (e) {
      console.warn("renderPianoSuggestions failed:", e);
    }
  }
  // --------------------------------------------------------------

  // メインのピアノ動画への切り替え
  if (data && data.ok && data.pianoVideoId) {
	  loadPianoCandidatesStop = true;
	  changePianoVideo = true;
	  pianoChanged = true;
    const idx = (typeof player.getPlaylistIndex === "function") ? player.getPlaylistIndex() : -1;
    const playlistId = (typeof PLAYLIST_ID === "string" && PLAYLIST_ID) ? PLAYLIST_ID : null;
    const ids = Array.isArray(PLAYLIST) ? PLAYLIST.slice() : null;
    const index = Math.max(0, idx);

    if (playlistId) {
      PLAYLIST_ID = undefined;
      PLAYLIST = player.getPlaylist();
      justLoadedPlaylistId = null;
      justLoadedVideoSlice = PLAYLIST.slice();
      PLAYLIST[index] = data.pianoVideoId;
      //player.loadPlaylist(PLAYLIST, index, 0, "large");

      player.stopVideo();
		// 少し間を空ける
		setTimeout(() => {
			START_APPLIED_VID = null;
	    	applyStartFromPlaylistOnUnstarted(PLAYLIST[index], true, index);
	    	changePianoVideo = false;
		  //player.loadPlaylist(PLAYLIST, index, 0, "large");
		}, 0);
      //justLoadedPlaylist = true;
      //justLoadedPlaylistId = PLAYLIST_ID;
      //justLoadedVideoSlice = null;
    } else if (Array.isArray(ids) && ids.length) {
      PLAYLIST_ID = undefined;
      PLAYLIST = ids.slice();
      PLAYLIST[index] = data.pianoVideoId;
      //player.loadPlaylist(PLAYLIST, index, 0, "large");
      applyStartFromPlaylistOnUnstarted(PLAYLIST[index], true, index);
      changePianoVideo = false;
      //justLoadedPlaylist = true;
      //justLoadedPlaylistId = null;
      //justLoadedVideoSlice = PLAYLIST;
    } else {
      try { player.loadVideoById(data.pianoVideoId); } catch (_) {}
    }

    if (data.recordPianoVideo) {
      showToast("登録されているピアノ動画が見つかりました");
    } else {
      showToast("検索からピアノ動画が見つかりました");
    }
    return;
  }

  // エラー
  /*const errMsg = data?.message || data?.code || "動画が見つかりませんでした。";
  showToast(errMsg);*/
}



// --- 「この候補ピアノ動画を再生」用(getPianoVideoの内部の処理を書いた感じだって) ---
function switchToPianoCandidate(videoId) {
  if (!player) return;
changePianoVideo = true;
pianoChanged = true;
  const idx = (typeof player.getPlaylistIndex === "function")
    ? player.getPlaylistIndex()
    : -1;

  const playlistId = (typeof PLAYLIST_ID === "string" && PLAYLIST_ID)
    ? PLAYLIST_ID
    : null;

  const ids = Array.isArray(PLAYLIST) ? PLAYLIST.slice() : null;
  const index = Math.max(0, idx);

  if (playlistId) {
    // 元は playlistId ベース → 現在の videoId 配列を取って差し替え
    PLAYLIST_ID = undefined;
    PLAYLIST = player.getPlaylist();
    justLoadedPlaylistId = null;
      justLoadedVideoSlice = PLAYLIST.slice();
    if (Array.isArray(PLAYLIST) && PLAYLIST.length) {
      PLAYLIST[index] = videoId;
      //このポーズは絶対に必要です。これがないと同じプレイリストIDが再度再生されます
      player.stopVideo();

		// 少し間を空ける
		setTimeout(() => {
			START_APPLIED_VID = null;
	    	applyStartFromPlaylistOnUnstarted(PLAYLIST[index], true, index);
		  //player.loadPlaylist(PLAYLIST, index, 0, "large");
		}, 0);
      
      //justLoadedPlaylist = true;
      
    } else {
      player.loadVideoById(videoId);
    }
  } else if (Array.isArray(ids) && ids.length) {
    // 元は videoId 配列
    PLAYLIST_ID = undefined;
    PLAYLIST = ids.slice();
    PLAYLIST[index] = videoId;
    player.loadPlaylist(PLAYLIST, index, 0, "large");
    //justLoadedPlaylist = true;
  } else {
    // 保険：単独再生
    try {
      player.loadVideoById(videoId);
    } catch (_) {}
  }

  if (typeof showToast === "function") {
    showToast("ピアノ動画に切り替えました");
  }
}

function updatePianoHighlight(currentVid) {
  const list = document.getElementById("pianoSuggestList");
  if (!list) return;

  const items = list.querySelectorAll(".piano-suggest-item");
  items.forEach(el => {
	  
    if (el.videoId === currentVid) {
      el.classList.add("is-current");
    } else {
      el.classList.remove("is-current");
    }
  });
}


let loadPianoCandidatesStop = false;
// --- ピアノ候補一覧の描画 ---
// thumbnails: { videoId -> {url,title} }, defaultVideoId: 最初に切り替える候補
function renderPianoSuggestions(thumbnails, defaultVideoId) {
  const card = document.getElementById("pianoSuggestCard");
  const list = document.getElementById("pianoSuggestList");
  if (!card || !list) return;

  // 一旦リセット
  list.innerHTML = "";
  card.style.display = "none";

  if (!thumbnails || typeof thumbnails !== "object") {
    return;
  }

  for (const [vid, t] of Object.entries(thumbnails)) {
    if (!t || !t.url) continue;
    const title = t.title || vid;

    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "piano-suggest-item";
    btn.onclick = () => {
		loadPianoCandidatesStop = true;		
		switchToPianoCandidate(vid); // 候補ごとに固有 videoId
		//updatePianoHighlight(vid);
	}
	btn.videoId = vid;
    const img = document.createElement("img");
    img.className = "piano-suggest-thumb";
    img.src = t.url;
    img.alt = title;

    const meta = document.createElement("div");
    meta.className = "piano-suggest-meta";

    const titleEl = document.createElement("div");
    titleEl.className = "piano-suggest-title";
    titleEl.textContent = title;

    const sub = document.createElement("div");
    sub.className = "piano-suggest-sub";
    sub.textContent = "クリックしてこのピアノ動画を再生";

    meta.appendChild(titleEl);
    meta.appendChild(sub);
    btn.appendChild(img);
    btn.appendChild(meta);

    list.appendChild(btn);
    
    
  }

  if (list.children.length > 0) {
    card.style.display = "block";
  }
  
  const baseVid = getVideoId();
  if (baseVid) {
    updatePianoHighlight(baseVid);
  }
}

// --- 「候補がありません」表示用 ---
// message: 表示したいメッセージ（省略時はデフォルト文言）
function renderPianoCandidatesEmpty(message) {
  const card = document.getElementById("pianoSuggestCard");
  const list = document.getElementById("pianoSuggestList");
  if (!card || !list) return;

  list.innerHTML = "";
  const msgEl = document.createElement("div");
  msgEl.className = "piano-suggest-empty";
  msgEl.textContent = message || "ピアノ動画候補が登録されていません";
  list.appendChild(msgEl);

  card.style.display = "block";
}



async function loadPianoCandidatesForCurrentVideo() {
  const card = document.getElementById("pianoSuggestCard");
  const list = document.getElementById("pianoSuggestList");
  if (!card || !list) return;

  let videoId = getVideoId();
  if (!videoId) {
    showToast("動画が再生されていません");
    return;
  }
  
  
  try {
	  let idx = -1;
    if (justLoadedVideoSlice != null && typeof player.getPlaylistIndex === "function") {
      const got = player.getPlaylistIndex();
      if (typeof got === "number" && got >= 0) idx = got;
      if(idx != -1)videoId = justLoadedVideoSlice[idx];
    }
  } catch (_) {}

  // 一回開き直したときのために毎回中身はクリアする
  list.innerHTML = "";
  // クリックしたらとりあえずパネル自体は開いておく
  card.style.display = "block";

  // ★ トーストは出さないように変更
  // showToast("対応するピアノ動画を探しています…");

  const url  = (APP_CTX || "") + "/piano/candidates?videoId=" + encodeURIComponent(videoId);
  const data = await servletResult(url);

  // サーバ側エラー or ok=false
  if (!data || !data.ok) {
    const msg = (data && (data.message || data.code)) || "ピアノ動画候補が見つかりませんでした";
    renderPianoCandidatesEmpty(msg);
    return;
  }

  // 配列が無い／空
  if (!Array.isArray(data.piano) || !data.piano.length) {
    renderPianoCandidatesEmpty("ピアノ動画候補が登録されていません");
    return;
  }

  const map = {};
  for (const item of data.piano) {
    if (!item || !item.videoId) continue;
    map[item.videoId] = {
      url:   item.thumbnail,
      title: item.title
    };
  }

  const keys = Object.keys(map);
  if (!keys.length) {
    renderPianoCandidatesEmpty("ピアノ動画候補が登録されていません");
    return;
  }

  // 以前出した renderPianoSuggestions(thumbnails, defaultVideoId) をそのまま使う
  renderPianoSuggestions(map, keys[0]);
}


document.addEventListener("DOMContentLoaded", () => {
  const plDetails = document.getElementById("playlistDetails");
  if (plDetails) {
    plDetails.addEventListener("toggle", () => {
      if (plDetails.open) {
        loadCurrentPlaylistPanel().catch(err =>
          console.warn("loadCurrentPlaylistPanel failed:", err)
        );
      }
    });
  }
});

// --- 現在プレイヤーに読み込まれているプレイリスト一覧を表示 ---
