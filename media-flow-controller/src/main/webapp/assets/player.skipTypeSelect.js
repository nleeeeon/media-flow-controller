(function(){
  const sel = document.getElementById("skipTypeSelect");
  if (!sel) return;

  // 初期値：グローバルに何か入っていればそれを優先
  if (window.CUR_SEGMENT_TYPE) {
    for (const opt of sel.options) {
      if (opt.value === window.CUR_SEGMENT_TYPE) {
        sel.value = opt.value;
        break;
      }
    }
  } else {
    window.CUR_SEGMENT_TYPE = sel.value || "default";
  }

  sel.addEventListener("change", () => {
    const t = sel.value || "default";
    window.CUR_SEGMENT_TYPE = t;

    const vid = typeof getVideoId === "function" ? getVideoId() : null;
    if (!vid) return;

    // この videoId の Type別キャッシュをクリアしたいので、
    // SEG_CACHE のキーを videoId + "::" + type にしている前提だと、
    // 「その videoId に関するエントリを全部消す」のが楽。
    if (SEG_CACHE && typeof SEG_CACHE.forEach === "function") {
      const keysToDelete = [];
      SEG_CACHE.forEach((v, k) => {
        if (k.startsWith(vid + "::")) keysToDelete.push(k);
      });
      keysToDelete.forEach(k => SEG_CACHE.delete(k));
    }

    // 現在の動画に対して、その Type の skip＆startSec を取り直す
    setupForCurrentVideo();
    startChecking();

    // 編集タブを開いているなら、編集リストも同じ Type で再ロード
    if (location.hash === "#/segments" && typeof loadUserSegs === "function") {
      loadUserSegs(vid);
    }
  });
})();
