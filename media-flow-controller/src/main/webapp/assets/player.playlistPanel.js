async function loadCurrentPlaylistPanel() {
  const card = document.getElementById("playlistCard");
  const list = document.getElementById("playlistList");
  if (!card || !list) return;

  if (!player) {
    list.innerHTML = "<p class='muted-text'>プレイヤーがまだ準備できていません。</p>";
    return;
  }

  // 1) プレイヤーから現在のプレイリスト videoId を取得
  let ids = [];
  try {
    if (typeof player.getPlaylist === "function") {
      //const arr = player.getPlaylist();
      const arr = justLoadedVideoSlice ?? player.getPlaylist();
      if (Array.isArray(arr)) {
        ids = arr.slice();
      }
    }
  } catch (_ignore) {}

  // 2) getPlaylist が使えない場合のフォールバックとして PLAYLIST を利用
  if (!ids.length && Array.isArray(PLAYLIST)) {
    ids = PLAYLIST.slice();
  }

  if (!ids.length) {
    list.innerHTML = "<p class='muted-text'>プレイリストが読み込まれていません。</p>";
    card.dataset.loadedIds = "";
    return;
  }

  const joined = ids.join(",");
  card.dataset.loadedIds = joined;
  list.innerHTML = "";  // 一旦クリア

  // サーバーからサムネ & タイトルを取得
  const url = (APP_CTX || "") + "/playlist/thumbs?ids=" + encodeURIComponent(joined);

  // piano のところで使っている servletResult を再利用前提
  let data;
  try {
    data = await servletResult(url);
  } catch (err) {
    console.warn("playlistMeta fetch failed:", err);
    list.innerHTML = "<p class='muted-text'>プレイリスト情報の取得に失敗しました。</p>";
    return;
  }

  if (!data || !data.ok) {
    const msg = (data && (data.message || data.code)) || "プレイリスト情報の取得に失敗しました。";
    list.innerHTML = "<p class='muted-text'>" + msg + "</p>";
    return;
  }

  const items = Array.isArray(data.items) ? data.items : [];
  if (!items.length) {
    list.innerHTML = "<p class='muted-text'>プレイリスト内の動画情報が登録されていません。</p>";
    return;
  }

  const currentVideoId = (typeof getVideoId === "function") ? getVideoId() : null;
  const frag = document.createDocumentFragment();

  // ids の順番に並べるため、id -> item のマップを作ってからループ
  const map = {};
  for (const item of items) {
    if (!item || !item.videoId) continue;
    map[item.videoId] = item;
  }

  ids.forEach((vid, idx) => {
    const item = map[vid] || { videoId: vid };
    const div = document.createElement("div");
    div.className = "playlist-item";
    div.dataset.videoId = vid;
    div.dataset.index = String(idx);

    if (currentVideoId && vid === currentVideoId) {
      div.classList.add("is-current");
    }

    // サムネ (DB になければ YouTube 標準サムネをフォールバック)
    const thumbUrl = item.thumbnail
      || ("https://i.ytimg.com/vi/" + vid + "/mqdefault.jpg");
    const img = document.createElement("img");
    img.className = "playlist-thumb";
    img.src = thumbUrl;
    img.alt = item.title || "(no title)";
    img.loading = "lazy";

    // タイトル
    const titleDiv = document.createElement("div");
    titleDiv.className = "playlist-title";
    titleDiv.textContent = item.title || "(タイトル未登録)";

    div.appendChild(img);
    div.appendChild(titleDiv);

    // クリックでその動画に移動
    div.onclick = () => {
      try {
		  const playlist = player.getPlaylist(); 
			const curVid = playlist[idx];
        if (typeof player.playVideoAt === "function") {
			if(vid == curVid){
	          player.playVideoAt(idx);
				
			}else{
				START_APPLIED_VID = null;
				PLAYLIST = justLoadedVideoSlice;
				applyStartFromPlaylistOnUnstarted(vid, true, idx);
			}
        } else if (typeof player.loadVideoById === "function") {
          player.loadVideoById(vid);
        }
      } catch (_e) {}
    };

    frag.appendChild(div);
  });

  list.appendChild(frag);
}

function updatePlaylistHighlight(currentVid) {
  const list = document.getElementById("playlistList");
  if (!list) return;

  const items = list.querySelectorAll(".playlist-item");
  if (!items.length) return;

  items.forEach(el => {
    if (el.dataset.videoId === currentVid) {
      el.classList.add("is-current");
    } else {
      el.classList.remove("is-current");
    }
  });
}
