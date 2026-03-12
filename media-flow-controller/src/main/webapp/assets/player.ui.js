/* ---- 便利: 画面メッセージ ---- */
function setMsg(s){
  const el = document.getElementById("aiMsg");
  if (el) el.textContent = s;
}

/* ---- IFrame API ローダ（このJSの後ろで <script src="https://www.youtube.com/iframe_api"></script> 読み込むのが推奨） ---- */
(function ensureYT(){
  if (!window.YT || !window.YT.Player) {
    // もしHTMLで読み込んでいなければ注入（重複は無視）
    if (!document.getElementById("yt-iframe-api")) {
      const sc = document.createElement("script");
      sc.id = "yt-iframe-api";
      sc.src = "https://www.youtube.com/iframe_api";
      document.head.appendChild(sc);
    }
  }
})();
