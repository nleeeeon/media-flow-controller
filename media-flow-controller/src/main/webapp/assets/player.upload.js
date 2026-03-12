//以下はデータアップロード時の処理です
(function(){

  const form = document.getElementById("uploadForm");
  if (!form) return;

  form.addEventListener("submit", async (ev) => {
    ev.preventDefault(); // ★ 通常送信を止める

    showToast("アップロードしました！");
const msgEl = document.getElementById("uploadMsg");
msgEl.textContent = "解析中です";
    const fd = new FormData(form);

    try {
      const resp = await fetch((APP_CTX || "") + "/api/upload/watch-history", {
        method: "POST",
        body: fd,
        credentials: "include"
      });

      const text = await resp.text();
      let data;
      try {
        data = JSON.parse(text);
      } catch (err) {
        showToast("JSON parse error: " + err + " raw=" + text.slice(0,200));
        return;
      }

      // --- ここから好きに処理 ---
      if (data.ok) {
        showToast("解析が完了しました");

      } else {
        showToast("Error: " + (data.message || data.code || "unknown"));
      }

    } catch(err) {
      showToast("Network error: " + err);
    }
	msgEl.textContent = "解析が終わりました";
  });
})();
