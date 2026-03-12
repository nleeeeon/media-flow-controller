<%@ page language="java" contentType="text/html; charset=UTF-8"
         pageEncoding="UTF-8" isELIgnored="true" %>



<!DOCTYPE html>
<html lang="ja">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Media Flow Controller</title>

<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600&family=Noto+Sans+JP:wght@300;400;500;700&display=swap" rel="stylesheet">
<link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/css/style.css">

</head>
<body>

<div class="app-container">
  <aside class="sidebar">
    <div class="brand">Media Flow Controller</div>
    <button class="nav-btn active" data-route="#/main">ダッシュボード</button>
    <button class="nav-btn" data-route="#/segments">スキップ編集</button>
    <button class="nav-btn" data-route="#/playlist">プレイリスト作成</button>
    
    
    <div style="margin-top: auto; padding: 12px;">
        <a href="<%= request.getContextPath() %>/auth/start" style="font-size:12px; color:var(--primary); text-decoration:none;">Google Login &rarr;</a>
    </div>
  </aside>

  <main class="main-content">
    
    <div class="hero-player-wrapper">
        <div class="yt-container">
            <div id="player" class="yt-player"></div>
        </div>
        
        <div class="player-controls-row">
            <div id="pianoChangePanel" style="display:none;">
                <button onclick="getPianoVideo()" class="btn-secondary">
                    <span>🎹</span> ピアノ版を探して再生
                </button>
                
                <div style="font-size:11px; color:#9CA3AF; margin-top:4px;">
                    ※YouTube上の対応動画を検索して切り替えます
                </div>
            </div>

            <div id="skipPanel" style="display:flex; align-items:center; gap:12px;">
                <label class="muted-text" style="cursor:pointer;">
                    <input type="checkbox" id="skipToggle" checked> スキップボタン表示
                </label>
                <!-- ★ Type 選択 UI（簡易版） -->
			    <label class="muted-text">
			        Type:
			        <select id="skipTypeSelect" class="input-modern" style="width:auto; padding:4px 8px; font-size:12px;">
			            <option value="default">default</option>
			            <option value="intro">intro</option>
			            <option value="live">live</option>
			        </select>
			    </label>
                <button id="skipSegmentButton" class="btn-primary" style="display:none; background:#F87171;">
                    この区間をスキップ
                </button>
                <span id="skipStatus" class="status-pill">Auto Skip ON</span>
            </div>
            
        </div>

        <div class="muted-text" style="margin-top:8px;">
             Source: <span id="skipSource">-</span> / Segments: <span id="segCount">0</span>
             <div id="errorBar"></div>
        </div>
        <div style="margin-top:6px;">
            <details style="font-size:12px; color:var(--text-light);">
                <summary>スキップ詳細</summary>
                <ul id="skipList"></ul>
            </details>
            <!-- 既存: Skip Details の直後あたり -->
		    <details id="pianoSuggestDetails"
		             style="font-size:12px; color:var(--text-light);">
		        <summary style="cursor:pointer;">
		            🎹 ピアノ候補動画
		        </summary>
		
		        <div id="pianoSuggestCard" class="card"
		             style="margin-top:8px; padding:12px 16px;"
		             data-loaded-video="">
		            <p class="muted-text" style="margin-bottom:8px;">
		                対応しているピアノ演奏動画の候補です。クリックするとその動画に切り替わります。
		            </p>
		            <div id="pianoSuggestList" class="piano-suggest-list"></div>
		        </div>
		    </details>
		    
			<!-- 現在のプレイリスト一覧 -->
			<details id="playlistDetails"
			         style="font-size:12px; color:var(--text-light); margin-top:12px;">
			  <summary style="cursor:pointer;">
			    📜 現在のプレイリスト
			  </summary>
			
			  <div id="playlistCard" class="card"
			       style="margin-top:8px; padding:12px 16px;"
			       data-loaded-ids="">
			    <p class="muted-text" style="margin-bottom:8px;">
			      現在プレイヤーに読み込まれている動画の一覧です。クリックするとその動画に移動します。
			    </p>
			    <div id="playlistList" class="playlist-list"></div>
			  </div>
			</details>

            
        </div>
    </div>


    <div id="view-main" class="panel active">
        <div class="grid-row">
            <div class="col-main">
                <div class="card" style="display:flex; align-items:center; gap:8px; background:linear-gradient(135deg, #ffffff 0%, #f0f7ff 100%); border:1px solid #dbeafe;">
                    <input id="prompt" class="input-modern" placeholder="デモではYoutubeのvideoId、playlistIdのみ動作します" style="border-radius:50px; border:1px solid #BFDBFE;">
                    <button onclick="searchPlaylist()" class="btn-primary">プレイリスト生成</button>
                    <div id="aiMsg" style="font-size:12px; color:var(--text-light);"></div>
                </div>

                <div class="card">
                    <h4>クイック操作</h4>
                    <p style="font-size:14px; color:var(--text-light); margin-bottom:20px;">
                        現在再生中の動画に対してアクションを実行します。
                    </p>
                    <div style="display:flex; gap:12px;">
                        <button id="openSegmentsFromMain" class="btn-secondary">スキップ区間を編集</button>
                        <button id="openPlaylistFromMain" class="btn-secondary">プレイリスト作成へ</button>
                    </div>
                </div>
                
                <div class="card">
                     <h4>履歴</h4>
                     <ul id="historyList" style="font-size:13px;"></ul>
                     <span id="historyMsg"></span>
                </div>
            </div>

            <div class="col-side">
                <!-- <div class="card">
                    <h4>Status Monitor</h4>
                    <div style="font-size:13px; display:grid; gap:8px;">
                        <div>Video ID: <code id="curVidLbl" style="background:#f3f4f6; padding:2px 4px; border-radius:4px;">-</code></div>
                        <div>Source: <span id="segSourceLbl">-</span></div>
                    </div>
                </div>とりあえずここは何もいいものが思いつかないのでいったんコメントアウト -->
                
                <div class="card">
                    <h4>データ取り込み</h4>
                    <form id="uploadForm" enctype="multipart/form-data">
					    <input type="file" name="file" accept=".json,.json.gz"
					           style="font-size:12px; width:100%; margin-bottom:8px;" />
					    <button type="submit" class="btn-secondary"
					            style="width:100%; justify-content:center;">履歴データアップロード</button>
					</form>
					<div id="uploadMsg" style="margin-top:8px; font-size:13px; color:var(--text-main);"></div>
                </div>
            </div>
        </div>
    </div>


    <div id="view-segments" class="panel">
        <div class="grid-row">
            <div class="card">
                <h4>再生位置・スキップ設定</h4>
                
                <p style="font-size:13px; color:var(--text-light); margin-bottom:16px;">
                    この動画の<strong>「再生開始位置（サビ）」</strong>や<strong>「スキップしたい区間（イントロ等）」</strong>を保存できます。<br>
                    次回から自動的に適用されます。
                </p>

                <div style="font-size:24px; font-weight:300; margin-bottom:16px; font-variant-numeric: tabular-nums;">
                    Current: <span id="curTime" style="color:var(--primary-hover);">0.00</span> s
                </div>
                
                <div style="background:#F3F9FF; padding:12px; border-radius:8px; margin-bottom:20px;">
                    <h5 style="margin:0 0 8px 0; font-size:12px; color:var(--primary-hover);">🎵 開始位置の設定 (サビ出し)</h5>
                    
                    <div style="display:flex; gap:12px; align-items:flex-end; flex-wrap:wrap;">
                        <label style="font-size:12px; font-weight:600; flex:1;">
                            再生開始秒数
                            <input id="segStartSecInput"
                                   type="number"
                                   min="0"
                                   step="0.1"
                                   class="input-modern"
                                   placeholder="例: 45.5">
                        </label>
                        
                        <label style="font-size:12px; font-weight:600; width:160px; display:flex; flex-direction:column;">
						    種類
						    <select id="segTypeInput"
						           class="input-modern"
						           style="width:100%; max-width:160px;">
						    		<option value="default">default</option>
						            <option value="intro">intro</option>
						            <option value="live">live</option>
						    </select>
						</label>

                    </div>
                    
                    <div style="display:flex; gap:8px; margin-top:8px;">
                        <button id="btnSetSegStartNow" type="button" class="btn-primary" style="font-size:12px; padding: 8px 16px;">
                            現在位置を取得
                        </button>
                        <button id="btnJumpSegStart" type="button" class="btn-secondary" style="font-size:12px; padding: 8px 16px;">
                            確認再生
                        </button>
                    </div>
                </div>
                
                <div style="border-top:1px dashed #E5E7EB; padding-top:16px;">
                     <h5 style="margin:0 0 8px 0; font-size:12px; color:#F87171;">✂️ 特定区間のスキップ (間奏など)</h5>
                     
                     <div style="display:flex; gap:8px; margin-bottom:20px;">
                        <button id="markStart" class="btn-secondary" style="flex:1;">
                            [ ここからスキップ
                        </button>
                        <button id="markEnd" class="btn-secondary" style="flex:1;">
                            ここまでスキップ ]
                        </button>
                    </div>
                </div>

                <button id="saveSeg" class="btn-primary" style="width:100%; justify-content:center; margin-top:8px;">
                    設定を保存 (Save)
                </button>
                
                <div style="margin-top:16px;">
                    <span style="font-size:12px; font-weight:600; color:var(--text-light);">登録済み設定リスト:</span>
                    <ul id="segList" style="margin:8px 0 0 0; padding-left:18px; font-size:13px; color:var(--text-main);"></ul>
                </div>
            </div>
            
            <div class="card">
                <h4>ショートカットキー</h4>
                <ul style="font-size:13px; line-height:2;">
                    <li><b>[</b> : スキップ開始点を指定</li>
                    <li><b>]</b> : スキップ終了点を指定＆追加</li>
                    <li><b>S</b> : 設定を保存</li>
                </ul>
                <p class="muted-text" style="margin-top:16px;">
                    ※ キーボードを使うと、動画を見ながらリアルタイムにスキップ区間を指定できて便利です。
                </p>
            </div>
        </div>
    </div>


    <div id="view-playlist" class="panel">
        
        <div id="nowInfoCard" class="card" style="border-left: 4px solid var(--primary);">
            <div class="track-layout">
                
                <div class="track-main">
                    <h4 style="margin-bottom:8px; color:var(--primary);">Now Playing</h4>
                    <h2 id="statTitle" class="track-title">Not Playing</h2>
                    
                    <div class="track-artist-row">
                        <span class="icon">🎵</span>
                        <span id="statArtist" style="font-weight:500;">-</span>
                        <span style="color:#D1D5DB;">/</span>
                        <span id="statSong" style="font-weight:500;">-</span>
                    </div>

                    <div class="track-id-badge">
                        ID: <span id="statVid">-</span>
                    </div>
                </div>

                <div class="track-side">
                    <div class="meta-grid">
                        <div class="meta-item">
                            <div class="meta-label">Period Plays</div>
                            <div class="meta-val" id="statPlaysRange">-</div>
                        </div>
                        <div class="meta-item">
                            <div class="meta-label">Total Plays</div>
                            <div class="meta-val" id="statPlaysAll">-</div>
                        </div>
                        
                        <div class="meta-item">
                            <div class="meta-label">First Played</div>
                            <div class="meta-val" id="statFirst">-</div>
                        </div>
                        <div class="meta-item">
                            <div class="meta-label">Last Played</div>
                            <div class="meta-val" id="statLast">-</div>
                        </div>
                    </div>
                </div>

            </div>
        </div>

        <hr style="border:0; height:1px; background:#E5E7EB; margin:32px 0;">

        <div class="card">
            <h4>再生履歴からプレイリストを生成</h4>
            <p class="muted-text" style="margin-bottom:24px;">過去の再生履歴から条件を指定してカスタムプレイリストを生成します。
            	<br>※ 本デモで参照できる視聴履歴データは 2025年4月～5月分です
            </p>
            <div class="muted-text"
			     style="margin-top:10px; padding:10px 12px; border:1px solid #e5e7eb;
			            background:#f9fafb; border-radius:10px; font-size:12px; line-height:1.6;">
			  <b>補足（音MAD）:</b>
			  視聴履歴に削除・非公開となった動画が含まれる場合、生成したプレイリストが再生できないことがあります。<br>
			  その際は <b>「ランダム」</b>に切り替えるか、<b>Limit を小さく</b>してお試しください。
			</div>
            <div class="playlist-gen-layout">
                
                <div class="gen-group">
                    <div class="gen-row-inputs">
                        <label style="font-size:12px; font-weight:600;">From (年月)
                            <input id="histFromYm" type="month" class="input-modern">
                        </label>
                        <label style="font-size:12px; font-weight:600;">To (年月)
                            <input id="histToYm" type="month" class="input-modern">
                        </label>
                    </div>

                    <div class="gen-row-inputs">
                        <label style="font-size:12px; font-weight:600;">Category
                            <select id="histGenre" class="input-modern">
                                <option value="">すべて</option>
                                <option value="MUSIC">通常音楽</option>
                                <option value="MAD">音MAD</option>
                                <option value="PIANO">ピアノ</option>
                            </select>
                        </label>
                        <label style="font-size:12px; font-weight:600;">Limit (曲数)
                            <input id="histLimit" type="number" min="1" max="200" value="100" class="input-modern">
                        </label>
                    </div>

                    <div style="margin-top:16px;">
                        <button id="histGenBtn" class="btn-primary" style="width:100%; justify-content:center; padding:14px;">
                            プレイリスト生成
                        </button>
                        <div id="histMsg" style="font-size:13px; color:var(--text-light); margin-top:8px; text-align:center;"></div>
                    </div>
                </div>

                <div class="gen-options-panel">
                    <h5 style="margin:0 0 12px 0; font-size:13px; color:var(--text-main);">生成方法</h5>
                    
                    <label style="display:flex; align-items:center; margin-bottom:12px; cursor:pointer;">
                        <input type="radio" name="histMode" value="count" checked style="margin-right:8px;">
                        <div>
                            <div style="font-size:14px; font-weight:600;">再生回数が多い順</div>
                        </div>
                    </label>
                    
                    <label style="display:flex; align-items:center; margin-bottom:24px; cursor:pointer;">
                        <input type="radio" name="histMode" value="random" style="margin-right:8px;">
                        <div>
                            <div style="font-size:14px; font-weight:600;">ランダムに選出</div>
                        </div>
                    </label>

                    
                </div>

            </div>
        </div>
    </div>

  </main>
</div>

<div id="toast">Skipped</div>
<div id="notification"></div>
<footer style="margin-top:40px; padding:20px; font-size:12px; text-align:center; color:#666; border-top:1px solid #ddd;">
  <div style="margin-bottom:10px;">
    本サービスは YouTube API Services を利用しています。
  </div>

  <div style="margin-bottom:14px;">
    <a href="https://www.youtube.com/t/terms" target="_blank" rel="noopener">YouTube 利用規約</a>
    /
    <a href="https://policies.google.com/privacy" target="_blank" rel="noopener">Google プライバシーポリシー</a>
    /
    <a href="<%=request.getContextPath()%>/terms.html">当サイト利用規約</a>
    /
    <a href="<%=request.getContextPath()%>/privacy.html">当サイトのプライバシーポリシー</a>
  </div>

  <div style="line-height:1.6;">
    本サイトは以下の外部 API を利用しています:<br>
    - YouTube Data API<br>
    - <a href="https://animethemes.moe/" target="_blank" rel="noopener">AnimeThemes API</a>（データ提供元・非公式利用）<br>
    - iTunes Search API
    <br>
    また、名前表記変換には以下を参照しています：<br>
    - JapaneseNameConverterRoot by Nolan Lawson (WTFPL)<br>
    
  </div>
</footer>



<script>
    // コンテキストパスの設定
    window.APP_CTX = (function(){
        var m = location.pathname.match(/^\/[^\/]+/);
        return m ? m[0] : "";
    })();

    (function(){
        const btn = document.getElementById("histGenBtn");
        if(btn) btn.onclick = ()=> histBuild();
        
        const routes = {
            "#/main":     "view-main",
            "#/segments": "view-segments",
            "#/playlist": "view-playlist",
        };
        const panels = Object.values(routes).map(id => document.getElementById(id));
        const navBtns = Array.from(document.querySelectorAll(".nav-btn"));
        
        function goto(hash){
            if(!routes[hash]) hash = "#/main";
            
            // パネル切り替え
            panels.forEach(el => el.classList.remove("active"));
            const target = document.getElementById(routes[hash]);
            if(target) target.classList.add("active");
            
            // ナビゲーション状態
            navBtns.forEach(b => b.classList.toggle("active", b.dataset.route === hash));
            location.hash = hash;
        }
        
        window.addEventListener("hashchange", ()=> goto(location.hash));
        navBtns.forEach(b => b.onclick = ()=> goto(b.dataset.route));
        
        const openSeg = document.getElementById("openSegmentsFromMain");
        if(openSeg) openSeg.onclick = ()=> goto("#/segments");
        
        const openPlay = document.getElementById("openPlaylistFromMain");
        if(openPlay) openPlay.onclick = ()=> goto("#/playlist");
        
        if(!location.hash) location.hash = "#/main"; else goto(location.hash);
    })();
    
</script>
<script src="assets/player.config.js"></script>
<script src="assets/player.ui.js"></script>
<script src="assets/player.youtube.js"></script>
<script src="assets/player.pianoGate.js"></script>
<script src="assets/player.skip.js"></script>
<script src="assets/player.playlistLoad.js"></script>
<script src="assets/player.histStats.js"></script>
<script src="assets/player.searchVideo.js"></script>
<script src="assets/player.saveGate.js"></script>
<script src="assets/player.historyPanel.js"></script>
<script src="assets/player.piano.js"></script>
<script src="assets/player.playlistPanel.js"></script>
<script src="assets/player.net.js"></script>
<script src="assets/player.segmentsEditor.js"></script>
<script src="assets/player.skipTypeSelect.js"></script>
<script src="assets/player.upload.js"></script>
<script src="<%=request.getContextPath()%>/assets/player.js"></script>
<script src="https://www.youtube.com/iframe_api"></script>
<script>
(function(){
	  const details = document.getElementById("pianoSuggestDetails");
	  const card    = document.getElementById("pianoSuggestCard");

	  if (!details || !card) return;

	  details.addEventListener("toggle", async () => {
	    if (!details.open) return;  // 閉じたときは何もしない

	    const videoId = getVideoId();
	    if (!videoId) {
	      showToast("動画が再生されていません");
	      return;
	    }

	    // ★常にそのときの動画で再読み込み
	    await loadPianoCandidatesForCurrentVideo();
	  });
	})();


</script>

</body>
</html>