/* ----- 以下のコードは動画のスキップの編集用のコードです ------- */

let notificationTimer = null;
function showNotification(text) {
  const t = $("notification");
  if (!t) return;
  t.textContent = text;
  t.style.display = "block";
  clearTimeout(notificationTimer);
  notificationTimer = setTimeout(()=> { t.style.display = "none"; }, TOAST_MS);
}
(function(){
    const curEl = document.getElementById('curTime');
    const segList = document.getElementById('segList');
    let pendingStart = null;
    let userSegs = [];
    let VID = null;
    let firstLi = null;
    const segTypeInput  = document.getElementById('segTypeInput');
    const segStartInput = document.getElementById('segStartSecInput');

    let currentSegType  = 'default';
    let currentStartSec = 0;
    
    const btnSetSegStartNow = document.getElementById('btnSetSegStartNow');
    const btnJumpSegStart   = document.getElementById('btnJumpSegStart');
    
    const pianoCandidatesArea = document.getElementById('pianoSuggestDetails');
    const btnFindPiano        = document.getElementById('pianoChangePanel');
    const playlistDetailsArea = document.getElementById('playlistDetails');
    
    function hidePianoUiForSegments() {
      [pianoCandidatesArea, btnFindPiano, playlistDetailsArea].forEach(el => {
        if (!el) return;

        // もともと display:none の要素は、そのままにしたいので
        const curDisp = window.getComputedStyle(el).display;
        if (curDisp === 'none') {
          // もともと非表示 → 触らない
          return;
        }
        // もともと表示されていた場合だけ、元の inline display を保存
        if (el.dataset.prevDisplay === undefined) {
          el.dataset.prevDisplay = el.style.display || '';
        }
        el.style.display = 'none';
      });
    }

    function restorePianoUiAfterSegments() {
      [pianoCandidatesArea, btnFindPiano, playlistDetailsArea].forEach(el => {
        if (!el) return;
        if (el.dataset.prevDisplay !== undefined) {
          // 自分が hidePianoUiForSegments で触ったものだけ戻す
          el.style.display = el.dataset.prevDisplay;
          delete el.dataset.prevDisplay;
        }
      });
    }

    // 「現在位置を反映」ボタン
    if (btnSetSegStartNow) {
      btnSetSegStartNow.onclick = () => {
        if (!player || typeof player.getCurrentTime !== "function") return;

        const t = Number(player.getCurrentTime() || 0);
        if (!Number.isFinite(t) || t < 0) return;

        currentStartSec = t;
        if (segStartInput) {
          segStartInput.value = t.toFixed(2);  // 少し見やすく
        }
      };
    }

    // 「その秒にジャンプ」ボタン
    if (btnJumpSegStart) {
      btnJumpSegStart.onclick = () => {
        if (!player || typeof player.seekTo !== "function") return;

        let v = currentStartSec || 0;

        // 入力欄に値があればそちらを優先
        if (segStartInput && segStartInput.value !== "") {
          const n = Number(segStartInput.value);
          if (Number.isFinite(n) && n >= 0) {
            v = n;
          }
        }

        player.seekTo(v, true);   // allowSeekAhead = true でシーク
      };
    }


    // 再生側からも参照できるようにグローバルに出しておく
    window.CUR_SEGMENT_TYPE = currentSegType;

// ===== 編集一時停止用のスナップショット =====
	const EDIT_CTX = {
	  active: false,
	  playlistId: null, // 元の PLAYLIST_ID を保存
	  ids: null,        // 元の PLAYLIST (配列) を保存
	  index: 0,         // 元のインデックス
	  seconds: 0        // 元の再生秒
	};
  
    
	
    window.addEventListener("hashchange", ()=>{
	  // 直前が編集モードで、今のハッシュがそれ以外なら復帰
	  if (EDIT_CTX?.active && location.hash !== "#/segments") {
	    exitEditAndResume();
	    restorePianoUiAfterSegments();
	  }
	});

    
    async function loadUserSegs(videoId){
      if (!videoId) return;

      // 今の入力 or 既定値
      let type =
        segTypeInput && segTypeInput.value.trim()
          ? segTypeInput.value.trim()
          : (currentSegType || 'default');

      if (!type) type = 'default';
      currentSegType = type;
      window.CUR_SEGMENT_TYPE = currentSegType;

      const url =
        (APP_CTX || '') +
        `/segments?videoId=${encodeURIComponent(videoId)}&type=${encodeURIComponent(type)}`;

      const res = await fetch(url, { cache:'no-store', credentials:'include' });
      const js  = await res.json();

      // サーバ側が selectedType / startSec / segments を返す想定
      if (js.selectedType) {
        currentSegType = js.selectedType;
        window.CUR_SEGMENT_TYPE = currentSegType;
      }
      if (segTypeInput) segTypeInput.value = currentSegType;

      currentStartSec = (typeof js.startSec === 'number') ? js.startSec : 0;
      if (segStartInput) segStartInput.value = currentStartSec;

      userSegs = (js.segments || []).map(s => ({
        start:  s.start,
        end:    s.end,
        reason: s.reason || 'user'
      }));
      renderList();
    }
    window.loadUserSegs = loadUserSegs; // グローバル公開

	// Type 入力変更で、そのタイプ用のセグメントを読み込み
    if (segTypeInput) {
      segTypeInput.addEventListener('change', () => {
        let t = (segTypeInput.value || '').trim();
        if (!t) t = 'default';
        currentSegType = t;
        window.CUR_SEGMENT_TYPE = currentSegType;

        if (VID) {
          loadUserSegs(VID);
        }
      });
    }

    
    function renderList(){
      segList.innerHTML='';
      console.log("renderList入ってuserSegs="+userSegs);
      userSegs.sort((a,b)=>a.start-b.start);
      userSegs.forEach((s,i)=>{
        const li = document.createElement('li');
        li.textContent = `${s.start.toFixed(2)} - ${s.end.toFixed(2)} (${s.reason}) `;
        const del = document.createElement('button');
        del.textContent='削除'; del.onclick=()=>{ userSegs.splice(i,1); renderList(); };
        li.appendChild(del);
        segList.appendChild(li);
      });
    }
    // ビュー入場時にロード。再生リストのまま再生されてるから動画の最後まで行ったら次が流されてややこしいことになりそうだから注意
    window.addEventListener("hashchange", ()=>{
      if(location.hash === "#/segments"){
        VID = getVideoId();
        if(!VID){ alert("videoIdが未取得です。まず再生を開始してください。"); return; }
        loadUserSegs(VID);
        enterEditForCurrent();
        hidePianoUiForSegments();
      }
    });
    
    
    
    
  
    document.getElementById('markStart').onclick=()=>{
		 pendingStart = player.getCurrentTime(); 
		 if(firstLi){
			 firstLi.textContent = `${pendingStart.toFixed(2)} - `;
		 }else{
			 firstLi = document.createElement('li');
	         firstLi.textContent = `${pendingStart.toFixed(2)} - `;
	         segList.appendChild(firstLi);
		 }
	};
    document.getElementById('markEnd').onclick=()=>{ 
      if(pendingStart==null){ alert('開始を先に打ってください'); return; }
      const e = player.getCurrentTime();
      if(e>pendingStart){
         userSegs.push({start:pendingStart, end:e, reason:'user'}); renderList();
         firstLi = null; 
      }else{
        alert('開始時間より後ろに設定してください'); 
        return;
      }
      pendingStart=null;
    };
    
    
    
    
    document.getElementById('saveSeg').onclick = async () => {
      if (!VID) VID = getVideoId();
      if (!VID) { alert("videoIdが未取得です"); return; }

      // Type を決定
      let type = currentSegType;
      if (segTypeInput) {
        const v = segTypeInput.value.trim();
        if (v) type = v;
      }
      if (!type) type = 'default';
      currentSegType = type;
      window.CUR_SEGMENT_TYPE = currentSegType;

      // startSec を決定
      let startSec = 0;
      if (segStartInput && segStartInput.value !== '') {
        const v = Number(segStartInput.value);
        if (Number.isFinite(v) && v >= 0) {
          startSec = v;
        }
      }
      currentStartSec = startSec;
console.log(SEG_CACHE);
      const payload = {
        videoId:  VID,
        type:     type,
        startSec: startSec,
        segments: userSegs
      };

      const res = await fetch((APP_CTX||'') + `/segments`, {
        method:'POST',
        headers:{'Content-Type':'application/json'},
        credentials:'include',
        body: JSON.stringify(payload)
      });

      if (res.ok || res.status === 204) {
        alert('保存しました');
        window.dispatchEvent(new CustomEvent("user-segments-updated", {
          detail: { videoId: VID }
        }));
      } else {
        alert('保存失敗: ' + res.status);
      }
    };

  
    // 時刻表示
    setInterval(()=>{ try { curEl.textContent = player ? player.getCurrentTime().toFixed(2) : '0.00'; } catch(e){} }, 200);
  
    // ショートカット（キーボード操作でもいけるっていうことです）
    window.addEventListener('keydown', (ev)=>{
      if(location.hash !== "#/segments") return;
      if(ev.key==='['){ pendingStart = player.getCurrentTime(); }
      if(ev.key===']'){ 
        if(pendingStart!=null){ const e=player.getCurrentTime(); if(e>pendingStart){ userSegs.push({start:pendingStart,end:e,reason:'user'}); renderList(); } pendingStart=null; }
      }
      if(ev.key==='s' || ev.key==='S'){ document.getElementById('saveSeg').click(); }
    });
  
    // /segments の再取得フック（既存player.js側で拾ってもOK）
    window.addEventListener("user-segments-updated", async (ev) => {
	  const vid =
	    ev.detail?.videoId ||
	    (typeof getVideoId === "function" ? getVideoId() : window.CUR_VIDEO_ID);
	// ★ Type を決定（なければ default）
	  const segType =
	    (typeof window.CUR_SEGMENT_TYPE === "string" && window.CUR_SEGMENT_TYPE.trim())
	      ? window.CUR_SEGMENT_TYPE.trim()
	      : "default";
	
	  // videoId + type ごとにキャッシュしたいので key を分ける
	  const cacheKey = vid + "::" + segType;
	  // 1) キャッシュ破棄
	  if (vid && SEG_CACHE.has(cacheKey)) {
		  
		  SEG_CACHE.delete(cacheKey);
	
	}
	  // 2) すぐ使う動画なら再取得して反映
	  if (vid && typeof setupForCurrentVideo === "function") {
	    // あなたの既存コードで /segments を取り直し → skipSegments を差し替える関数
	    await setupForCurrentVideo(); // 引数は任意
	    startChecking();
	  }
	});
	
	
	
	// ----- 編集モードに入る（今再生中の1本だけを“待機”でセット） -----
	function enterEditForCurrent(){
	  if (!player) return;
	
	//スキップを自動的にオフ
		const toggle = $("skipToggle");
		if(toggle.checked){
			toggle.checked = !toggle.checked;  // ON/OFF切り替え
			toggle.dispatchEvent(new Event("change"));  // 発火
			
		}

	  // 1) いまの状態をスナップショット
	  const vid = getVideoId();
	  const idx = (typeof player.getPlaylistIndex === "function") ? player.getPlaylistIndex() : -1;
	  const secs = Number(player?.getCurrentTime?.() || 0);
	
	  EDIT_CTX.active = true;
	  EDIT_CTX.playlistId = (typeof PLAYLIST_ID === "string" && PLAYLIST_ID) ? PLAYLIST_ID : null;
	  EDIT_CTX.ids = (Array.isArray(PLAYLIST) ? PLAYLIST.slice() : null);
	  EDIT_CTX.index = Math.max(0, idx);
	  EDIT_CTX.seconds = Math.max(0, Math.floor(secs));
	
	  // 2) 単一動画モードへ（自動遷移しない）
	  if (vid) {
	    try {
	      player.loadVideoById(vid, EDIT_CTX.seconds, "large");  // ★待機状態でセット
	      
	      setMsg("編集モード: 自動遷移を停止しました");
	      showNotification("編集モード: 自動遷移を停止しました");
	    } catch(e) {
	      console.warn("enterEditForCurrent failed:", e);
	    }
	  }
	}

	// ----- 編集モードを抜けてプレイリストに戻る -----
	function exitEditAndResume(){
	  if (!EDIT_CTX.active) return;
	  const { playlistId, ids, index, seconds } = EDIT_CTX;
	  EDIT_CTX.active = false;
	
	  try {
		  preStartSec = seconds;
	    if (playlistId) {
	      // 元は YouTube 上のプレイリスト
	      player.loadPlaylist({ listType: "playlist", list: playlistId,
	                            index: Math.max(0,index), startSeconds: /*Math.max(0, seconds)*/Number(player.getCurrentTime() || 0) });
	      //justLoadedPlaylist = true;
	      
	    } else if (Array.isArray(ids) && ids.length) {
	      // 元は動画ID配列
	      PLAYLIST_ID = undefined;
	      PLAYLIST = ids.slice();
	      player.loadPlaylist(PLAYLIST, Math.max(0,index), /*Math.max(0, seconds)*/Number(player.getCurrentTime() || 0), "large");
	      //justLoadedPlaylist = true;
	    } else {
	      // それでも情報が無ければ、今の単一動画をそのまま再生に戻す
	      player.playVideo();
	    }
	    // UI的に編集モードの表示を戻したい場合
	    if (location.hash && location.hash.startsWith("#/segments")) {
	      try { history.replaceState(null, "", location.pathname); } catch(_) {}
	    }
	    setMsg("通常モードに復帰しました");
	  } catch(e) {
	    console.warn("exitEditAndResume failed:", e);
	  }
	}
})();
