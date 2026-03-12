async function histBuild() {
  const fromYm = (document.getElementById("histFromYm")?.value || "").trim(); // 'YYYY-MM'
  const toYm   = (document.getElementById("histToYm")?.value   || "").trim();
  const mode   = (document.querySelector('input[name="histMode"]:checked')?.value) || "count";
  const limit  = parseInt(document.getElementById("histLimit")?.value || "50", 10);
  const genre = (document.getElementById("histGenre")?.value || "");
  const create = !!document.getElementById("histCreate")?.checked;

  const m = (t)=> (document.getElementById("histMsg").textContent = t);
  const setMsg = m; // 既存setMsgと混乱しないようローカル参照

  // 入力チェック
  if (!fromYm || !toYm) { setMsg("開始/終了の月を指定してください。"); return; }
  if (fromYm > toYm) { setMsg("開始が終了より後になっています。"); return; }
  if (!Number.isFinite(limit) || limit < 1) { setMsg("件数は1以上にしてください。"); return; }

  // 入力読んだ直後に追加
window.__HIST_RANGE__ = { fromYm, toYm };


  const params = new URLSearchParams({
    fromYm, toYm, mode, limit: String(limit)
  });
  if (genre !== "") params.set("genre", genre);
  if (create) params.set("create", "1");

  const url = (APP_CTX || "") + "/youtube/histPlaylist?" + params.toString();

  setMsg(`生成中… (${fromYm}〜${toYm} / ${mode}${genre!==""?` / cat=${genre}`:""} / ${limit}件)`);

  let text = "";
  try {
    const resp = await fetch(url, { method:"GET", cache:"no-store", credentials:"include" });
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

  if (data && !data.ok) {
    setMsg("ユーザーIDの設定ができてないです。設定してください。");
    return;
  }

  // playlistId 優先（YouTubeに保存した場合など）今のところは使う予定ないかな
  if (data && data.ok && data.playlistId) {
    PLAYLIST = undefined;
    PLAYLIST_ID = data.playlistId;
    setMsg(`プレイリストを読み込みます: ${data.playlistId}`);
    if (player && typeof player.loadPlaylist === "function") {
      try { player.loadPlaylist({ listType: "playlist", list: data.playlistId }); } catch (_){}
      justLoadedPlaylist = true;
      justLoadedPlaylistId = PLAYLIST_ID;
      justLoadedVideoSlice = null;
    }
    return;
  }

  // ids で返ってきた場合（サイト内再生）
  const ids = Array.isArray(data?.ids) ? data.ids
            : Array.isArray(data?.videoIds) ? data.videoIds
            : [];
  if (ids.length) {
    setMsg(`動画${ids.length}件 → 年齢制限チェック中…`);
    await afterIdsCollected(ids);
    return;
  }

  setMsg("エラー: " + (data?.message || data?.code || "該当データがありませんでした。"));
}
// 再生開始ごとに呼ぶ
async function updateNowPlayingStats(videoId) {
  try {
    // 表示リセット
    document.getElementById("statVid").textContent = videoId || "-";
    document.getElementById("statTitle").textContent = "-";
    document.getElementById("statPlaysRange").textContent = "-";
    document.getElementById("statFirst").textContent = "-";
    document.getElementById("statLast").textContent = "-";
    document.getElementById("statPlaysAll").textContent = "-";
    const elArtist = document.getElementById("statArtist");
    const elSong   = document.getElementById("statSong");
    if (elArtist) elArtist.textContent = "-";
    if (elSong)   elSong.textContent   = "-";

    // タイトルは IFrame API から
    try {
      const vd = player?.getVideoData?.();
      if (vd && vd.title) document.getElementById("statTitle").textContent = vd.title;
    } catch(_) {}

    // 期間パラメータ
    const fromYm = window.__HIST_RANGE__?.fromYm || "";
    const toYm   = window.__HIST_RANGE__?.toYm   || "";

    const params = new URLSearchParams({ videoId });
    if (fromYm) params.set("fromYm", fromYm);
    if (toYm)   params.set("toYm", toYm);

    const url = (APP_CTX || "") + "/youtube/songStats?" + params.toString();

    const resp = await fetch(url, { method:"GET", cache:"no-store", credentials:"include" });
    const text = await resp.text();
    let data;
    try { data = JSON.parse(text); }
    catch(e){ console.warn("songStats JSON parse error:", e, text); return; }

    if (!data?.ok) {
      console.warn("songStats error:", data?.code || data?.message || data);
      return;
    }

 	if (elArtist && typeof data.artist === "string" && data.artist.length > 0) {
      elArtist.textContent = data.artist;
    }
    if (elSong && typeof data.song === "string" && data.song.length > 0) {
      elSong.textContent = data.song;
    }
    
    // 期間内
    const r = data.range || {};
    if (typeof r.plays === "number") document.getElementById("statPlaysRange").textContent = String(r.plays);
    if (r.first) document.getElementById("statFirst").textContent = fmtDateTime(r.first);
    if (r.last)  document.getElementById("statLast").textContent  = fmtDateTime(r.last);

    // 累計
    const a = data.all || {};
    if (typeof a.plays === "number") document.getElementById("statPlaysAll").textContent = String(a.plays);

  } catch (err) {
    console.warn("updateNowPlayingStats failed:", err);
  }
}

// 日付フォーマット（簡易）
function fmtDateTime(iso){
  try {
    const d = new Date(iso);
    if (isNaN(d.getTime())) return iso;
    const pad = (n)=> String(n).padStart(2,"0");
    const y = d.getFullYear(), m = pad(d.getMonth()+1), dd = pad(d.getDate());
    const hh = pad(d.getHours()), mm = pad(d.getMinutes());
    return `${y}-${m}-${dd} ${hh}:${mm}`;
  } catch(_){ return iso; }
}

/* ---- メイン: AI検索（GETで副作用なし） ---- */
