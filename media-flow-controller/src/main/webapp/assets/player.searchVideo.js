async function searchPlaylist() {
  const input = document.getElementById("prompt");
  const q = (input?.value || "").trim();
  if (!q) { setMsg("入力してください。"); return; }

  setMsg(`解析中… (${q})`);

  const url = (APP_CTX || "") + "/youtube/playlist?q=" + encodeURIComponent(q);
  let text = "";
  try {
    const resp = await fetch(url, { method: "GET", cache: "no-store", credentials: "include" });
    text = await resp.text();
  } catch (err) {
    setMsg("エラー: ネットワーク失敗: " + (err?.message || err));
    return;
  }

  let data;
  try { data = JSON.parse(text); }
  catch (err) {
    setMsg("エラー: サーバからのJSONが不正です（" + (err?.message || err) + "） raw=" + text.slice(0,200));
    return;
  }

  // 未ログイン（サーバが NOT_AUTH を返す想定）
  if (data && data.ok === false && (data.code === "NOT_AUTH" || data.error === "NOT_AUTH")) {
    setMsg("デモ環境ではキーワードによる検索はできません");
    //setMsg("Googleにログインすると検索できます。リダイレクトします…");
    //デプロイしてるときは実行させないlocation.href = (APP_CTX||"") + "/auth/start?next=" + encodeURIComponent(location.pathname);
    return;
  }

  // 1) プレイリストIDが返ってきた → そのまま読み込む（API不要）
  if (data && data.ok && data.playlistId) {
    PLAYLIST = undefined;
    PLAYLIST_ID = data.playlistId;
    setMsg(`プレイリストを読み込みます: ${data.playlistId}`);
    if (player && typeof player.loadPlaylist === "function") {
		//applyStartFromPlaylistOnUnstartedメソッドでもう一度loadPlaylistされるけどプレイリストIDだと先頭のIDがわからないのでいったんこれで
      try { player.loadPlaylist({ listType: "playlist", list: data.playlistId }); } catch(_){}
      justLoadedPlaylist = true;
      justLoadedPlaylistId = PLAYLIST_ID;
      justLoadedVideoSlice = null;
    }
    return;
  }

  // 2) 動画ID配列 → 年齢制限チェックへ
  const ids = Array.isArray(data?.videoIds) ? data.videoIds
            : Array.isArray(data?.ids) ? data.ids
            : [];
  if (ids.length) {
    setMsg(`動画${ids.length}件 → 年齢制限チェック中…`);
    await afterIdsCollected(ids);
    return;
  }

  // 3) エラー
  const errMsg = data?.message || data?.code || "動画が見つかりませんでした。";
  setMsg("エラー: " + errMsg);
}

/* ---- 年齢制限/埋め込み可否チェック（デフォルト許可） ---- */
async function afterIdsCollected(idsRaw) {
  const ids = Array.isArray(idsRaw) ? idsRaw.slice() : [];
  if (!ids.length) throw new Error("動画が見つかりませんでした。");

  // 50件ずつ ageCheck
  const chunks = [];
  for (let i = 0; i < ids.length; i += 50) chunks.push(ids.slice(i, i + 50));

  const allowedSet = new Set();
  const hardBlockSet = new Set(); // ★ ageRestricted のみハード除外
  const softNotEmb = new Set();   // ★ 情報表示用（事前除外しない）

  try {
    for (const chunk of chunks) {
      const url = (APP_CTX||"") + "/youtube/ageCheck?ids=" + encodeURIComponent(chunk.join(","));
      const res = await fetch(url, { cache: "no-store", credentials: "include" });
      const raw = await res.text();

      let j = {};
      try { j = JSON.parse(raw); } catch(_){}

      const allowed = Array.isArray(j?.allowed) ? j.allowed : [];
      const ageR   = Array.isArray(j?.ageRestricted) ? j.ageRestricted : [];
      const notEmb = Array.isArray(j?.notEmbeddable) ? j.notEmbeddable : [];

      allowed.forEach(v => allowedSet.add(v));
      ageR.forEach(v => hardBlockSet.add(v));   // ← ハード除外は年齢制限のみ
      notEmb.forEach(v => softNotEmb.add(v));   // ← “注意”として表示に回す
    }
  } catch (e) {
    console.warn("ageCheck failed:", e);
    setMsg("注意: 年齢制限チェックに失敗しました（ネットワーク/レート制限）。確認のうえ再生を続行します。");
    loadIdsIntoPlayer(ids);
    return;
  }

  // …（AgeCheck呼び出しとセット構築まではそのまま）…
	
	// ★ デフォルト許可：ハードブロック（=年齢制限）のみ除外
	const allowedOrdered = ids.filter(v => !hardBlockSet.has(v));
	
	//本来はここで年齢制限等の報告をするけど、結局除外して再生しかないからいったんコメントアウト
	/*if (hardBlockSet.size > 0 || softNotEmb.size > 0) {
	  // ← ここでゲートを出す（自動再生に進まない）
	  setMsg(`注意: 年齢制限 ${hardBlockSet.size} 件 / 埋め込み不可の可能性 ${softNotEmb.size} 件。どうしますか？`);
	  showGatePanel(ids, hardBlockSet, softNotEmb);
	  return;
	}*/
	
	// 何も問題なければそのまま再生
	setMsg(`動画${allowedOrdered.length}件 → プレイヤーへ読み込みました。`);
	loadIdsIntoPlayer(allowedOrdered.length ? allowedOrdered : ids);

}
// --- YouTubeに保存（/youtube/buildPlaylist） --- 必要がなくなった
// 置き換え：saveToYoutube(ids)
