async function saveToYoutube(ids,hardBlockSet) {
  setMsg("YouTubeにプレイリストを作成中…");
  const params = new URLSearchParams({
    ids: ids.join(","),
    title: `AI連続再生 ${new Date().toLocaleString()}`,
    privacy: "unlisted"
  });
  const res = await fetch((APP_CTX||"") + "/youtube/buildPlaylist?" + params.toString(), {
    method: "GET",
    credentials: "include",
    cache: "no-store"
  });
  const raw = await res.text(); let j = {};
  try { j = JSON.parse(raw); } catch(_) {}
	
	if(j && j.ok && j.playlistId && hardBlockSet.size > 0){
		setMsg("作成しました。YouTubeの再生リストにリダイレクトします");
		location.href = "https://www.youtube.com/playlist?list=" + encodeURIComponent(j.playlistId);
		return;
	}
  if (j && j.ok && j.playlistId) {
    setMsg("作成しました。読み込みます…");
    PLAYLIST = undefined; PLAYLIST_ID = j.playlistId;
    if (player && typeof player.loadPlaylist === "function") {
      try { player.loadPlaylist({ listType: "playlist", list: j.playlistId }); } catch(_){}
      justLoadedPlaylist = true;
      justLoadedPlaylistId = PLAYLIST_ID;
      justLoadedVideoSlice = null;
    }
    return;
  }

  // 自動対処
  const code = j?.code || "";
  if (code === "NOT_AUTH" || code === "INSUFFICIENT_PERMISSIONS") {
    setMsg("権限が不足しています。Google同意画面へ移動します…");
    location.href = (APP_CTX||"") + "/auth/start?force=1&next=" + encodeURIComponent(location.pathname);
    return;
  }
  if (code === "YOUTUBE_SIGNUP_REQUIRED") {
    setMsg("YouTubeチャンネルを作成してください。作成後にもう一度保存を実行してください。");
    try { window.open("https://www.youtube.com/channel_switcher", "_blank"); } catch(_){}
    return;
  }

  // それ以外は詳細メッセージ
  setMsg("保存に失敗: " + (j?.reason ? `${j.reason}: ` : "") + (j?.message || raw));
}


// --- ゲートパネル表示（年齢制限/埋め込み不可の可能性があるときに呼ぶ） ---
function showGatePanel(inputIds, hardBlockSet, softNotEmbSet) {
  const gate = document.getElementById("gatePanel");
  if (!gate) {
    // パネルが無ければデフォルトで年齢制限のみ除外して再生
    const finalIds = inputIds.filter(v => !hardBlockSet.has(v));
    loadIdsIntoPlayer(finalIds.length ? finalIds : inputIds);
    return;
  }
  // カウントとリストを表示
  const ageCnt = document.getElementById("ageCount");
  const embCnt = document.getElementById("embCount");
  const ageList = document.getElementById("ageList");
  const embList = document.getElementById("embList");
  if (ageCnt)  ageCnt.textContent = String(hardBlockSet.size);
  if (embCnt)  embCnt.textContent = String(softNotEmbSet.size);
  if (ageList) ageList.textContent = Array.from(hardBlockSet).join(", ");
  if (embList) embList.textContent = Array.from(softNotEmbSet).join(", ");

  const allowedMinusAge = inputIds.filter(v => !hardBlockSet.has(v));
  const chkAlsoExcludeEmb = document.getElementById("chkAlsoExcludeEmb");
  const btnExclude = document.getElementById("btnExclude");
  const btnPlayAll = document.getElementById("btnPlayAll");
  const btnSave = document.getElementById("btnSaveThenPlay");

  if (btnExclude) btnExclude.onclick = () => {
    const excludeEmb = !!(chkAlsoExcludeEmb && chkAlsoExcludeEmb.checked);
    const finalIds = excludeEmb ? allowedMinusAge.filter(v => !softNotEmbSet.has(v)) : allowedMinusAge;
    gate.style.display = "none";
    setMsg(`動画${finalIds.length}件 → プレイヤーへ読み込みました。`);
    loadIdsIntoPlayer(finalIds.length ? finalIds : allowedMinusAge);
  };
  if (btnPlayAll) btnPlayAll.onclick = () => {
    // 「全部再生」でも年齢制限だけは除外（埋め込み不可の可能性は含める）
    const finalIds = allowedMinusAge;
    gate.style.display = "none";
    setMsg(`動画${finalIds.length}件 → プレイヤーへ読み込みました。`);
    loadIdsIntoPlayer(finalIds.length ? finalIds : inputIds);
  };
  if (btnSave) btnSave.onclick = async () => {
    gate.style.display = "none";
    await saveToYoutube(inputIds,hardBlockSet);
  };

  gate.style.display = "block";
}
