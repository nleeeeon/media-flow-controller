async function servletResult(url){
  let text = "";
  try {
    const resp = await fetch(url, { method: "GET", cache: "no-store" });
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
    setMsg("Googleにログインするとピアノ動画に変換できます。リダイレクトします…");
    location.href = (APP_CTX||"") + "/auth/start?next=" + encodeURIComponent(location.pathname);
    return;
  }
  if(data && data.ok === false){
	  showToast(data.message);//本当はちゃんとしたエラーメッセージを作らないといけない
	  return data;
  }
  return data;
}
