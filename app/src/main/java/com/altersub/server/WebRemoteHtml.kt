package com.altersub.server

object WebRemoteHtml {

    fun getHtml(): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>AlterSub TV Remote</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
        body { background: #121212; color: #FFFFFF; padding: 16px; -webkit-tap-highlight-color: transparent; }
        .card { background: #1E1E1E; border-radius: 12px; padding: 16px; margin-bottom: 16px; box-shadow: 0 4px 12px rgba(0,0,0,0.4); }
        h1 { font-size: 20px; font-weight: 700; color: #FFE500; display: flex; align-items: center; gap: 8px; }
        .title { font-size: 18px; font-weight: 600; margin-top: 8px; color: #FFFFFF; }
        .subtitle { font-size: 13px; color: #888888; margin-top: 4px; }
        .status-badge { display: inline-block; background: #00E676; color: #000; font-size: 11px; font-weight: 700; padding: 2px 8px; border-radius: 10px; margin-left: auto; }
        
        .btn-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin-top: 12px; }
        button { background: #2A2A2A; border: 1px solid #3A3A3A; color: #FFFFFF; padding: 12px 6px; font-size: 14px; font-weight: 600; border-radius: 8px; cursor: pointer; transition: 0.1s; }
        button:active { background: #FFE500; color: #000000; }
        .btn-accent { background: #FFE500; color: #000; border: none; font-weight: 700; }
        .btn-play { background: #E50914; color: #FFF; border: none; grid-column: span 2; }
        
        .offset-display { text-align: center; font-size: 28px; font-weight: 700; color: #FFE500; margin: 12px 0; font-variant-numeric: tabular-nums; }
        
        .search-box { display: flex; gap: 8px; margin-top: 12px; }
        input[type="text"] { flex: 1; background: #2A2A2A; border: 1px solid #3A3A3A; color: #FFF; padding: 10px 12px; border-radius: 8px; font-size: 14px; outline: none; }
        input[type="file"] { display: none; }
        
        .track-item { background: #262626; border-radius: 8px; padding: 12px; margin-top: 8px; display: flex; justify-content: space-between; align-items: center; cursor: pointer; border: 1px solid transparent; }
        .track-item.active { border-color: #FFE500; background: #2E2A14; }
        .track-name { font-size: 14px; font-weight: 500; }
        .track-source { font-size: 11px; color: #AAA; margin-top: 2px; }
        .badge { background: #333; font-size: 10px; padding: 2px 6px; border-radius: 4px; color: #DDD; }
    </style>
</head>
<body>
    <div class="card">
        <div style="display:flex; align-items:center;">
            <h1>🎬 AlterSub Remote</h1>
            <span class="status-badge" id="tvStatus">ONLINE</span>
        </div>
        <div class="title" id="detectedTitle">Scanning TV...</div>
        <div class="subtitle" id="activeTrackName">No active subtitle track</div>
    </div>

    <div class="card">
        <h2 style="font-size:15px; color:#AAA;">TIMING & OFFSET SYNC</h2>
        <div class="offset-display" id="offsetText">+0.00s</div>
        <div class="btn-grid">
            <button onclick="adjustOffset(-1000)">-1.0s</button>
            <button onclick="adjustOffset(-250)">-250ms</button>
            <button onclick="adjustOffset(250)">+250ms</button>
            <button onclick="adjustOffset(1000)">+1.0s</button>
        </div>
        <div class="btn-grid" style="margin-top:8px;">
            <button class="btn-play" onclick="togglePlay()" id="playPauseBtn">PLAY / PAUSE</button>
            <button onclick="adjustOffset(-offsetValue)" style="grid-column: span 2;">RESET TO 0</button>
        </div>
    </div>

    <div class="card">
        <h2 style="font-size:15px; color:#AAA;">MANUAL SEARCH & UPLOAD</h2>
        <div class="search-box">
            <input type="text" id="searchInput" placeholder="Search movie or series...">
            <button class="btn-accent" onclick="searchManual()">Search</button>
        </div>
        <div style="margin-top:12px;">
            <label for="fileUpload" style="display:block; width:100%; text-align:center; background:#2A2A2A; border:1px dashed #666; padding:12px; border-radius:8px; cursor:pointer; font-size:13px; font-weight:600; color:#FFE500;">
                📁 Upload .SRT from Phone
            </label>
            <input type="file" id="fileUpload" accept=".srt,.vtt" onchange="uploadFile(this)">
        </div>
    </div>

    <div class="card">
        <h2 style="font-size:15px; color:#AAA;">AVAILABLE SUBTITLE TRACKS</h2>
        <div id="trackList">
            <div style="font-size:13px; color:#666; text-align:center; padding:16px;">No tracks loaded</div>
        </div>
    </div>

    <script>
        let offsetValue = 0;
        let isPlaying = false;

        async function fetchStatus() {
            try {
                const res = await fetch('/api/status');
                const data = await res.json();
                
                document.getElementById('detectedTitle').innerText = data.title || "No Content Detected";
                document.getElementById('activeTrackName').innerText = data.activeTrack ? "Active: " + data.activeTrack : "No active track";
                
                offsetValue = data.offsetMs || 0;
                document.getElementById('offsetText').innerText = (offsetValue >= 0 ? '+' : '') + (offsetValue / 1000).toFixed(2) + 's';
                
                isPlaying = data.isPlaying;
                document.getElementById('playPauseBtn').innerText = isPlaying ? "PAUSE CLOCK" : "START CLOCK";
                
                renderTracks(data.tracks || [], data.activeTrackId);
            } catch(e) {}
        }

        function renderTracks(tracks, activeId) {
            const list = document.getElementById('trackList');
            if (tracks.length === 0) {
                list.innerHTML = '<div style="font-size:13px; color:#666; text-align:center; padding:16px;">No tracks found</div>';
                return;
            }
            list.innerHTML = tracks.map(t => `
                <div class="track-item ${'$'}{t.id === activeId ? 'active' : ''}" onclick="selectTrack('${'$'}{t.id}')">
                    <div>
                        <div class="track-name">${'$'}{t.title}</div>
                        <div class="track-source">${'$'}{t.source}</div>
                    </div>
                    <span class="badge">${'$'}{t.language.toUpperCase()}</span>
                </div>
            `).join('');
        }

        async function adjustOffset(delta) {
            await fetch('/api/offset?delta=' + delta, { method: 'POST' });
            fetchStatus();
        }

        async function togglePlay() {
            await fetch('/api/toggle-play', { method: 'POST' });
            fetchStatus();
        }

        async function selectTrack(id) {
            await fetch('/api/select-track?id=' + encodeURIComponent(id), { method: 'POST' });
            fetchStatus();
        }

        async function searchManual() {
            const query = document.getElementById('searchInput').value;
            if (!query) return;
            document.getElementById('trackList').innerHTML = '<div style="font-size:13px; color:#AAA; text-align:center; padding:16px;">Searching...</div>';
            await fetch('/api/search?q=' + encodeURIComponent(query), { method: 'POST' });
            setTimeout(fetchStatus, 1500);
        }

        async function uploadFile(input) {
            if (!input.files || input.files.length === 0) return;
            const file = input.files[0];
            const formData = new FormData();
            formData.append('subtitle', file);

            document.getElementById('activeTrackName').innerText = "Uploading " + file.name + "...";
            await fetch('/api/upload', { method: 'POST', body: formData });
            setTimeout(fetchStatus, 500);
        }

        setInterval(fetchStatus, 2000);
        fetchStatus();
    </script>
</body>
</html>
        """.trimIndent()
    }
}
