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
        .clock-display { text-align: center; font-size: 15px; color: #AAA; margin-top: 16px; font-variant-numeric: tabular-nums; }

        .input-row { display: flex; gap: 8px; margin-top: 12px; }
        input[type="text"] { flex: 1; background: #2A2A2A; border: 1px solid #3A3A3A; color: #FFF; padding: 10px 12px; border-radius: 8px; font-size: 14px; outline: none; }
        input[type="file"] { display: none; }
        
        .track-item { background: #262626; border-radius: 8px; padding: 12px; margin-top: 8px; display: flex; justify-content: space-between; align-items: center; cursor: pointer; border: 1px solid transparent; }
        .track-item.active { border-color: #FFE500; background: #2E2A14; }
        .track-name { font-size: 14px; font-weight: 500; }
        .track-source { font-size: 11px; color: #AAA; margin-top: 2px; }
        .badge { background: #333; font-size: 10px; padding: 2px 6px; border-radius: 4px; color: #DDD; }
        .style-row { display: flex; align-items: center; gap: 8px; margin-top: 10px; }
        .style-label { width: 64px; color: #AAA; font-size: 13px; }
        .style-value { flex: 1; font-size: 15px; font-weight: 600; font-variant-numeric: tabular-nums; }
        .color-btn.active { border-color: #FFE500; color: #FFE500; }
        .warning { display: none; background: #4A1C1C; color: #FFB4A9; border-radius: 8px; padding: 10px 12px; margin-top: 12px; font-size: 13px; }
        .pin-input { letter-spacing: 6px; font-size: 20px !important; text-align: center; }
        [hidden] { display: none !important; }
    </style>
</head>
<body>
    <div class="card" id="pairCard" hidden>
        <h1>🎬 AlterSub Remote</h1>
        <div class="subtitle" style="font-size:14px; color:#CCC; margin-top:12px;">
            Enter the 6-digit PIN shown on the TV. If you don't see one, open AlterSub on the TV.
        </div>
        <form class="input-row" onsubmit="pair(); return false;">
            <input type="text" id="pinInput" class="pin-input" inputmode="numeric" autocomplete="one-time-code" maxlength="6" placeholder="PIN">
            <button class="btn-accent" type="submit">Pair</button>
        </form>
        <div class="warning" id="pairError"></div>
    </div>

    <div id="remote" hidden>
    <div class="card">
        <div style="display:flex; align-items:center;">
            <h1>🎬 AlterSub Remote</h1>
            <span class="status-badge" id="tvStatus">ONLINE</span>
        </div>
        <div class="title" id="detectedTitle">Scanning TV...</div>
        <div class="subtitle" id="activeTrackName">No active subtitle track</div>
        <div class="warning" id="overlayWarning"></div>
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
        <div class="clock-display" id="clockText">Clock 0:00:00</div>
        <div class="input-row">
            <input type="text" id="seekInput" placeholder="Player time, e.g. 41:23">
            <button class="btn-accent" onclick="seekClock()">Set time</button>
        </div>
    </div>

    <div class="card">
        <h2 style="font-size:15px; color:#AAA;">SUBTITLE STYLE</h2>
        <div class="style-row">
            <span class="style-label">Size</span>
            <span class="style-value" id="styleSize">–</span>
            <button onclick="setStyle('sizeStep=-1')">A−</button>
            <button onclick="setStyle('sizeStep=1')">A+</button>
        </div>
        <div class="style-row">
            <span class="style-label">Position</span>
            <span class="style-value" id="stylePosition">–</span>
            <button onclick="setStyle('positionStep=-1')">▲ Up</button>
            <button onclick="setStyle('positionStep=1')">▼ Down</button>
        </div>
        <div class="style-row">
            <span class="style-label">Color</span>
            <span id="styleColors" style="display:flex; gap:8px; flex:1;"></span>
        </div>
        <button onclick="setStyle('reset=1')" style="width:100%; margin-top:12px;">RESET STYLE</button>
    </div>

    <div class="card">
        <h2 style="font-size:15px; color:#AAA;">MANUAL SEARCH & UPLOAD</h2>
        <div class="input-row">
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
    </div>

    <script>
        let offsetValue = 0;
        let isPlaying = false;

        // Pairing token from the TV's PIN. Storage can be unavailable (private browsing), so it also lives in memory.
        const TOKEN_KEY = 'altersubToken';
        let token = '';
        try { token = localStorage.getItem(TOKEN_KEY) || ''; } catch (e) {}

        function saveToken(value) {
            token = value;
            try {
                if (value) localStorage.setItem(TOKEN_KEY, value); else localStorage.removeItem(TOKEN_KEY);
            } catch (e) {}
        }

        function showPairing(message) {
            document.getElementById('remote').hidden = true;
            document.getElementById('pairCard').hidden = false;
            const error = document.getElementById('pairError');
            error.textContent = message || '';
            error.style.display = message ? 'block' : 'none';
        }

        function showRemote() {
            document.getElementById('pairCard').hidden = true;
            document.getElementById('remote').hidden = false;
        }

        // Every API call carries the token; a 401 means this phone isn't paired (or was unpaired on the TV)
        async function api(path, options) {
            if (!token) return null; // The pairing card is already showing
            const request = Object.assign({ method: 'POST' }, options);
            request.headers = { 'X-AlterSub-Token': token };
            const res = await fetch(path, request);
            if (res.status === 401) {
                saveToken('');
                showPairing();
                return null;
            }
            return res;
        }

        async function pair() {
            const input = document.getElementById('pinInput');
            try {
                const res = await fetch('/api/pair?pin=' + encodeURIComponent(input.value.trim()), { method: 'POST' });
                const data = await res.json();
                if (res.ok && data.token) {
                    saveToken(data.token);
                    input.value = '';
                    showRemote();
                    fetchStatus();
                } else {
                    showPairing(data.error || 'Pairing failed');
                }
            } catch (e) {
                showPairing("Can't reach the TV. Check that the phone is on the same Wi-Fi.");
            }
        }

        async function fetchStatus() {
            if (!token) return; // Not paired; the 2 s poll mustn't wipe a pairing error message
            try {
                const res = await api('/api/status', { method: 'GET' });
                if (!res) return;
                showRemote();
                const data = await res.json();
                
                document.getElementById('detectedTitle').innerText = data.title || "No Content Detected";
                document.getElementById('activeTrackName').innerText = data.activeTrack ? "Active: " + data.activeTrack : "No active track";

                const warning = document.getElementById('overlayWarning');
                warning.textContent = data.overlayError || "";
                warning.style.display = data.overlayError ? "block" : "none";

                offsetValue = data.offsetMs || 0;
                document.getElementById('offsetText').innerText = (offsetValue >= 0 ? '+' : '') + (offsetValue / 1000).toFixed(2) + 's';
                
                isPlaying = data.isPlaying;
                document.getElementById('playPauseBtn').innerText = isPlaying ? "PAUSE CLOCK" : "START CLOCK";
                document.getElementById('clockText').innerText = "Clock " + formatTime(data.positionMs || 0) + (isPlaying ? " (running)" : " (paused)");

                renderStyle(data.style);
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
            await api('/api/offset?delta=' + delta);
            fetchStatus();
        }

        // Accepts "83", "41:23" or "1:05:10" (seconds may be fractional); returns ms or null
        function parseTime(text) {
            const parts = text.trim().split(':');
            if (parts.length > 3) return null;
            let seconds = 0;
            for (const raw of parts) {
                const part = raw.trim();
                if (part === '' || isNaN(part) || Number(part) < 0) return null;
                seconds = seconds * 60 + Number(part);
            }
            return Math.round(seconds * 1000);
        }

        function formatTime(ms) {
            const total = Math.floor(ms / 1000);
            const h = Math.floor(total / 3600);
            const m = Math.floor((total % 3600) / 60);
            const s = total % 60;
            return h + ':' + String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0');
        }

        async function seekClock() {
            const input = document.getElementById('seekInput');
            const ms = parseTime(input.value);
            if (ms === null) {
                alert('Enter the time shown in the player, e.g. 41:23 or 1:05:10');
                return;
            }
            await api('/api/seek?positionMs=' + ms);
            input.value = '';
            fetchStatus();
        }

        async function setStyle(params) {
            await api('/api/style?' + params);
            fetchStatus();
        }

        function renderStyle(style) {
            if (!style) return;
            document.getElementById('styleSize').textContent = Math.round(style.textSizeSp) + ' sp';
            document.getElementById('stylePosition').textContent = Math.round(style.verticalPosition * 100) + '% down';

            // Colour buttons come from the server's palette, built with textContent (no HTML injection)
            const row = document.getElementById('styleColors');
            if (row.childElementCount === 0) {
                for (const name of style.colors) {
                    const button = document.createElement('button');
                    button.className = 'color-btn';
                    button.dataset.color = name;
                    button.textContent = name.charAt(0).toUpperCase() + name.slice(1);
                    button.addEventListener('click', () => setStyle('color=' + encodeURIComponent(name)));
                    row.appendChild(button);
                }
            }
            for (const button of row.children) {
                button.classList.toggle('active', button.dataset.color === style.color);
            }
        }

        async function togglePlay() {
            await api('/api/toggle-play');
            fetchStatus();
        }

        async function selectTrack(id) {
            await api('/api/select-track?id=' + encodeURIComponent(id));
            fetchStatus();
        }

        async function searchManual() {
            const query = document.getElementById('searchInput').value;
            if (!query) return;
            document.getElementById('trackList').innerHTML = '<div style="font-size:13px; color:#AAA; text-align:center; padding:16px;">Searching...</div>';
            await api('/api/search?q=' + encodeURIComponent(query));
            setTimeout(fetchStatus, 1500);
        }

        async function uploadFile(input) {
            if (!input.files || input.files.length === 0) return;
            const file = input.files[0];
            const formData = new FormData();
            formData.append('subtitle', file);

            document.getElementById('activeTrackName').innerText = "Uploading " + file.name + "...";
            await api('/api/upload', { body: formData });
            setTimeout(fetchStatus, 500);
        }

        setInterval(fetchStatus, 2000);
        if (token) fetchStatus(); else showPairing();
    </script>
</body>
</html>
        """.trimIndent()
    }
}
