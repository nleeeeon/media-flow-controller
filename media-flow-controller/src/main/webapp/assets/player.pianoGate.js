async function showPianoChangeButton(){
	let showFlag;
	const vid = getVideoId();
	
	const url = (APP_CTX || "") + "/video/judge?videoId=" + vid;
  let data = await servletResult(url);

  // 1) プレイリストIDが返ってきた → そのまま読み込む（API不要）
  if (data && data.judge && !data.isPiano) {
	  showFlag = true;
  }else if(data){
 	 showFlag = false;
  }else{
   // 3) エラー
   const errMsg = data?.message || data?.code || "判定できませんでした";
   showToast(errMsg);//本当はちゃんとしたエラーメッセージを作らないといけない
	showFlag = false; 
  }
	     
	
	
	if(showFlag){
		const gate = document.getElementById("pianoChangePanel");
		gate.style.display = "block";
		
	}else{
		const gate = document.getElementById("pianoChangePanel");
		gate.style.display = "none";
	}
}
