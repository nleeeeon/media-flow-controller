/* player.js — Tomcat10/Java21 / API最小構成
 * - /ai/playlist?q=... : { ok:true, playlistId? , videoIds? }
 * - /youtube/ageCheck?ids=... : { allowed[], ageRestricted[], notEmbeddable[] }（無くても再生は進む）
 * - デフォルト許可：ageRestricted だけを事前除外。notEmbeddable は“警告のみ”
 * - 保存系は別サーブレット（/youtube/buildPlaylist）想定
 */

/* ---- コンテキストパス ---- */
const APP_CTX = (function () {
  if (window.APP_CTX) return window.APP_CTX;

  const segs = location.pathname.split("/").filter(s => s.length > 0);
  if (segs.length === 0) {
    // "/" だけ → ルートデプロイ
    return "";
  }

  const first = segs[0];

  // 先頭セグメントに "." が含まれていたらファイル名とみなして
  // コンテキストパスなし（Render の /index.jsp など）
  if (first.includes(".")) {
    return "";
  }

  // /youtube-tools/index.jsp → "/youtube-tools" という従来の Tomcat 用
  return "/" + first;
})();


/* ---- グローバル ---- */
let player = null;
let PLAYER_READY = false;
let PLAYLIST_ID = undefined;   // /ai/playlist が playlistId を返した場合に使用
let PLAYLIST = undefined;      // videoIds を受け取った時に使用

let skipSegments = [];
let checkTimer = null;
let lastSeekAtMs = 0;
let SKIP_ENABLED = true;               // ★追加：トグル状態
let CURRENT_SOURCE = "unknown";        // ★追加：UI表示用
const TOAST_MS = 900;                  // ★追加：トースト表示時間
const SEG_CACHE = new Map();			// 追加：ブラウザ内キャッシュ（動画ID -> セグメント配列）
const PREFETCH_AHEAD = 3;			// 先読み数
function $(id){ return document.getElementById(id); }
