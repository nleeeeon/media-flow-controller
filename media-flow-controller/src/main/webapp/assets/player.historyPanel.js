(async function loadHistory(){
  try {
    const res = await fetch((APP_CTX||"") + "/api/history", { cache:"no-store", credentials:"include" });
    if(!res.ok){ throw new Error("HTTP " + res.status); }
    const data = await res.json();
    const arr = data.items;
    const ul = document.getElementById("historyList");
    ul.innerHTML = "";
    arr.forEach(item => {
	  const q = String(item.cache_key || "");
	  const li = document.createElement("li");
	  const btn = document.createElement("button");
	  btn.type = "button";
	  btn.className = "hist-btn";
	  btn.textContent = `${q}  |  ${item.created_at}`;
	  btn.dataset.query = q;              // クエリは data 属性に保持
	  btn.addEventListener("click", onHistoryClick);
	  li.appendChild(btn);
	  ul.appendChild(li);
	});
    if(arr.length === 0) document.getElementById("historyMsg").textContent = "履歴はまだありません。";
  } catch(e){
    document.getElementById("historyMsg").textContent = "履歴の取得に失敗しました: " + e.message;
    console.warn(e);
  }
})();
function onHistoryClick(e){
  const qRaw = e.currentTarget.dataset.query || "";
  // 先頭の "q:" が付いてる場合だけ安全に外す。本来はついてないはずだけどね。
  const q = qRaw.startsWith("q:") ? qRaw.substring(2) : qRaw;

  const input = document.getElementById("aiPrompt");
  if (input) input.value = q;

  // そのまま生成
  if (typeof window.aiBuild === "function") {
    window.aiBuild();
  } else {
    console.warn("aiBuild() が見つかりません。player.js の読み込み順を確認してください。");
  }
}

/* ---- 外から呼べるように ---- */
window.aiBuild = aiBuild;
window.loadIdsIntoPlayer = loadIdsIntoPlayer;

/* ---- デバッグ用ワンライナー ----
 * console: window.PLAY = ids => loadIdsIntoPlayer(ids);
 */
window.PLAY = (ids) => loadIdsIntoPlayer(ids);
