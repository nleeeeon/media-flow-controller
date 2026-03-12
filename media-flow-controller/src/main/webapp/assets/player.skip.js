/* ----   ここからスキップに関するコード（改）  -------- */

// 現在の動画のスキップ位置情報
/*let skipSegments = [];
let CURRENT_SOURCE = "unknown";
let checkTimer = null;
let lastSeekAtMs = 0;
let SKIP_ENABLED = true;              // トグル用（初期ON想定）
let SEG_CACHE = new Map();            // /segments の結果キャッシュ
let LAST_SETUP_VID = null;
let PREFETCH_AHEAD = 3;               // 先読みする件数
let TOAST_MS = 2000;*/

// 「いま提案中のスキップ区間」
let currentSkipTarget = null;

// ★ 追加：開始位置を適用済みの videoId（ループ防止）
let START_APPLIED_VID = null;

// ★ 追加：UNSTARTED → 再ロード中かどうか
let RELOAD_FOR_START = false;

/** 現在の動画のスキップ情報を取得 */
async function setupForCurrentVideo() {
  stopChecking();
  skipSegments = [];
  currentSkipTarget = null;
  hideSkipButton();
  clearErrorBar();
  bindSkipUI();

  const vid = getVideoId();
  LAST_SETUP_VID = vid;
  if (!vid) {
    CURRENT_SOURCE = "unknown";
    renderSkipUI();
    return;
  }

  try {
    const { source, segments, startSec } = await fetchSegments(vid); // 共通関数を利用
    CURRENT_SOURCE = source || "unknown";
    skipSegments = segments && segments.length ? segments : [];
    console.log("[segments]", vid, "source=" + CURRENT_SOURCE,
                "startSec=" + startSec, skipSegments);

    
  } catch (e) {
    CURRENT_SOURCE = "default";
    skipSegments = [];
    showErrorBar("セグメント取得に失敗したため、スキップ提案は行いません");
    console.warn("segments fetch failed:", e);
  }


  renderSkipUI();
}

let preStartSec = 0;
// ★ UNSTARTED(-1) のときに呼ぶ：
//    その動画の startSec を取り、同じプレイリスト＋同じ index でロードし直す。
async function applyStartFromPlaylistOnUnstarted(vid, justPlay, index) {
	const changePianovideo = changePianoVideo;
	const pianochanged = pianoChanged;//なぜかわからないが、ここで変数を記録しとかないと、fetchSegmentsの直後にfalseになる
	
  if (!player || !vid) return;

  // 既にこの動画には適用済みなら何もしない（無限ループ防止）
  if (START_APPLIED_VID === vid) return;

  // すでに別の再ロード処理中ならスキップ
  if (RELOAD_FOR_START) return;

  // startSec を取得（キャッシュ優先）
  let data;
  if (!justPlay && SEG_CACHE.has(vid)) {
    data = SEG_CACHE.get(vid);
  } else {
    data = await fetchSegments(vid);  // { source, segments, startSec }
  }

  const startSec =
    typeof data.startSec === "number" && data.startSec > 0
      ? data.startSec
      : 0;

  // 適用済みフラグだけは立てておく（0 の場合もこれ以上触らない）
  START_APPLIED_VID = vid;
  /*下のpreStartSecは必要です。これがないと前回の動画の開始位置から開始されます*/
  if (!justPlay && !justLoadedPlaylist && !startSec && !preStartSec && (changePianovideo || !pianochanged)) return;
  
  // 一応一時停止（-1の時点ではほぼ意味がないが、冪等）
  try { player.pauseVideo(); } catch (_) {}

  // 現在のプレイリスト index を取得
  let idx = 0;
  try {
    if (!justPlay && typeof player.getPlaylistIndex === "function") {
      const got = player.getPlaylistIndex();
      if (typeof got === "number" && got >= 0) idx = got | 0;
    }
  } catch (_) {}

	if(index != -1)idx = index;
  
	// ★ ここで「すでに再生が進んでいないか」をチェック
  try {
    const st  = player.getPlayerState();
    const cur = (typeof player.getCurrentTime === "function")
                  ? player.getCurrentTime()
                  : 0;

    // すでに PLAYING で、ある程度進んでいるならユーザー操作とみなして何もしない
    if (!justPlay && st === YT.PlayerState.PLAYING && cur > 1) {
      console.log("[startPos] already playing; skip reload", vid, "t=", cur);
      START_APPLIED_VID = vid;  // 二度とこの動画には触らない
      return;
    }
  } catch (e) {
    console.warn("applyStartFromPlaylistOnUnstarted state check error:", e);
  }
  
  RELOAD_FOR_START = true;
  preStartSec = startSec;
  try {
    // YouTube 側のプレイリストIDを使っている場合
    if (PLAYLIST_ID) {
		if(!changePianovideo){
			PLAYLIST_ID = justLoadedPlaylistId;
			loadPianoCandidatesStop = false;
		}
      player.loadPlaylist({
        listType: "playlist",
        list: PLAYLIST_ID,
        index: idx,
        startSeconds: startSec
      });
    }
    // ローカル配列 PLAYLIST を使っている場合
    else if (Array.isArray(PLAYLIST) && PLAYLIST.length) {
      PLAYLIST_ID = undefined;  // 配列モード
      if(!changePianovideo){
		  PLAYLIST = justLoadedVideoSlice;
		  loadPianoCandidatesStop = false;
	  }
      player.loadPlaylist(PLAYLIST, idx, startSec, "large");
    }
    // それも無い（単発）の場合だけ単発でロード
    else {
      player.loadVideoById(vid, startSec, "large");
    }
  } catch (e) {
    console.warn("applyStartFromPlaylistOnUnstarted failed:", e);
  } finally {
    // 次の -1 は「再ロードの結果」なので、ここでだけ RELOAD_FOR_START を true にしておく
    // onPlayerStateChange 側でこれを見てスキップする
  }
  
}





/**
 * ★追加：次の数件を事前取得。
 * fetchSegments 関数でキャッシュに入るのでここで呼ぶ意味がある。
 */
async function prefetchNextSegments() {
  try {
    if (!player || typeof player.getPlaylist !== "function") return;
    const ids = player.getPlaylist();
    const idx = player.getPlaylistIndex();
    if (!Array.isArray(ids) || idx == null) return;

    const tasks = [];
    for (let i = 1; i <= PREFETCH_AHEAD; i++) {
      const j = idx + i;
      if (j < ids.length) {
        const nextVid = ids[j];
        if (nextVid && !SEG_CACHE.has(nextVid)) {
          tasks.push(fetchSegments(nextVid)); // fire & await all
        }
      } else {
        break;
      }
    }
    if (tasks.length) await Promise.allSettled(tasks);
  } catch (e) {
    // 先読みはベストエフォート
    console.warn("prefetchNextSegments failed:", e);
  }
}

function startChecking() {
  stopChecking();
  checkTimer = setInterval(checkAndSkip, 250);
}

function stopChecking() {
  if (checkTimer) {
    clearInterval(checkTimer);
    checkTimer = null;
  }
}

/**
 * ★変更ポイント：
 *   - ここでは「自動で seekTo しない」
 *   - スキップ区間に入っている場合に「スキップボタンを表示」するだけ
 */
function checkAndSkip() {
  if (!SKIP_ENABLED) {
    currentSkipTarget = null;
    hideSkipButton();
    return;
  }
  if (!skipSegments || skipSegments.length === 0) {
    currentSkipTarget = null;
    hideSkipButton();
    return;
  }
  if (!player || typeof player.getCurrentTime !== "function") {
    currentSkipTarget = null;
    hideSkipButton();
    return;
  }

  const t = player.getCurrentTime();
  const now = performance.now();
  if (now - lastSeekAtMs < 500) {
    // 直前に seek した直後は判定をスキップ
    return;
  }

  let target = null;
  for (let i = 0; i < skipSegments.length; i++) {
    const s = skipSegments[i];
    // 通常の区間
    if (t >= s.start && t < s.end) { target = s; break; }
    // 0〜end のような先頭区間
    if (s.start === 0 && t < s.end) { target = s; break; }
  }

  if (target) {
    // スキップ候補区間にいる → ボタンを更新して表示
    currentSkipTarget = target;
    showSkipButtonFor(target);
  } else {
    // スキップ区間外 → ボタンは隠す
    currentSkipTarget = null;
    hideSkipButton();
  }
}

// 共通：/segments フェッチ関数（キャッシュ利用）
async function fetchSegments(videoId) {
  if (!videoId) return { source: "none", segments: [], startSec: 0 };

  // ★ Type を決定（なければ default）
  const segType =
    (typeof window.CUR_SEGMENT_TYPE === "string" && window.CUR_SEGMENT_TYPE.trim())
      ? window.CUR_SEGMENT_TYPE.trim()
      : "default";

  // videoId + type ごとにキャッシュしたいので key を分ける
  const cacheKey = videoId + "::" + segType;
  if (SEG_CACHE.has(cacheKey)) {
	  console.log(SEG_CACHE.get(cacheKey)+"これを返した");
    return SEG_CACHE.get(cacheKey);
  }

  const url =
    (APP_CTX ? (APP_CTX + "/segments") : "/segments") +
    "?videoId=" + encodeURIComponent(videoId) +
    "&type="    + encodeURIComponent(segType);

  try {
    const res = await fetch(url, { cache: "no-store", credentials: "include" });
    if (!res.ok) throw new Error("HTTP " + res.status);
    const data = await res.json(); // { source, selectedType, startSec, segments, ... }

    const raw = Array.isArray(data.segments) ? data.segments : [];
    const arr = raw
      .filter(s =>
        typeof s.start === "number" &&
        typeof s.end   === "number" &&
        s.end > s.start)
      .sort((a, b) => a.start - b.start);

    const startSec = (typeof data.startSec === "number") ? data.startSec : 0;

    const result = {
      source: data.source || "user",
      segments: arr,
      startSec,
      selectedType: data.selectedType || segType
    };

    SEG_CACHE.set(cacheKey, result);
    return result;
  } catch (e) {
    console.warn("fetchSegments error:", e);
    const fb = { source: "default", segments: [], startSec: 0, selectedType: segType };
    SEG_CACHE.set(cacheKey, fb);
    return fb;
  }
}



// UI反映
function renderSkipUI() {
  const toggle = $("skipToggle");
  const status = $("skipStatus");
  const src = $("skipSource");
  const cnt = $("segCount");
  const list = $("skipList");

  if (toggle) toggle.checked = SKIP_ENABLED;
  if (status) status.textContent = SKIP_ENABLED ? "ON" : "OFF";
  if (src) src.textContent = CURRENT_SOURCE;

  if (cnt) cnt.textContent = Array.isArray(skipSegments) ? skipSegments.length : 0;

  if (list) {
    list.innerHTML = "";
    (skipSegments || []).forEach((s, i) => {
      const li = document.createElement("li");
      const why = s.reason ? `, reason=${s.reason}` : "";
      li.textContent = `#${i + 1}: [${s.start.toFixed(2)} – ${s.end.toFixed(2)}]${why}`;
      list.appendChild(li);
    });
    if (list.innerHTML === "") {
      list.append("スキップ区間は無いです");
    }
  }
}

/**
 * スキップ関連UIのイベント登録
 *  - トグル
 *  - スキップボタン
 */
function bindSkipUI() {
  const toggle = $("skipToggle");
  if (toggle && !toggle.__bound) {
    toggle.__bound = true;
    toggle.addEventListener("change", () => {
      SKIP_ENABLED = !!toggle.checked;
      // OFF にしたらボタンも消す
      if (!SKIP_ENABLED) {
        currentSkipTarget = null;
        hideSkipButton();
      }
      renderSkipUI();
    });
  }

  const btn = $("skipSegmentButton");
  if (btn && !btn.__bound) {
    btn.__bound = true;
    btn.addEventListener("click", () => {
      // ★ここがユーザー操作による seek のみ
      if (!player || !currentSkipTarget) return;
      try {
        player.seekTo(currentSkipTarget.end, true);
        lastSeekAtMs = performance.now();
        showToast(
          `スキップ: ${currentSkipTarget.start.toFixed(1)}→${currentSkipTarget.end.toFixed(1)}s`
        );
      } catch (_) {
        // 失敗時は何もしない
      } finally {
        // 押したあとはボタンを隠す
        currentSkipTarget = null;
        hideSkipButton();
      }
    });
  }
}

// スキップボタンの表示/非表示制御
function showSkipButtonFor(seg) {
  const btn = $("skipSegmentButton");
  if (!btn) return;

  // ボタンの文言を、対象区間に合わせて更新
  const start = seg.start.toFixed(1);
  const end = seg.end.toFixed(1);
  btn.textContent = `${start}〜${end}秒をスキップ`;
  btn.style.display = "inline-block";
}

function hideSkipButton() {
  const btn = $("skipSegmentButton");
  if (!btn) return;
  btn.style.display = "none";
}

// エラーバー
function showErrorBar(msg) {
  const el = $("errorBar");
  if (!el) return;
  el.style.display = "block";
  el.textContent = msg;
}
function clearErrorBar() {
  const el = $("errorBar");
  if (!el) return;
  el.style.display = "none";
  el.textContent = "";
}

// トースト
let toastTimer = null;
function showToast(text) {
  const t = $("toast");
  if (!t) return;
  t.textContent = text || "スキップしました";
  t.style.display = "block";
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { t.style.display = "none"; }, TOAST_MS);
}

function getVideoId() {
  try {
    if (player && typeof player.getPlaylist === "function") {
      const ids = player.getPlaylist();
      const idx = player.getPlaylistIndex();
      if (Array.isArray(ids) && idx >= 0 && idx < ids.length) return ids[idx];
    }
    if (player && typeof player.getVideoUrl === "function") {
      const url = new URL(player.getVideoUrl());
      const v = url.searchParams.get("v");
      if (v) return v;
    }
  } catch (_) {}
  return null;
}
/* ------  ここまでスキップに関するコード（改）  ----- */
