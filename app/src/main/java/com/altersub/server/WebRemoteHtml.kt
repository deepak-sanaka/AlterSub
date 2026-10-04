package com.altersub.server

/**
 * The phone remote: one self-contained page. Nothing loads from the internet: the UI font (AlterSub Sans,
 * from Google Sans Flex) is served by the TV itself and cached by the browser. Server data is only ever
 * inserted with textContent (KI-8).
 */
object WebRemoteHtml {

    fun getHtml(): String = HTML

    // Built once; the TV serves the same string to every request
    private val HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="theme-color" content="#0E0E11">
<meta name="color-scheme" content="dark">
<title>AlterSub Remote</title>
<style>
    /* swap: text shows at once in the system font, then switches when the font arrives (cached for a week) */
    @font-face { font-family: "AlterSub Sans"; font-weight: 400; font-display: swap; src: url("/fonts/app-sans-regular.ttf") format("truetype"); }
    @font-face { font-family: "AlterSub Sans"; font-weight: 500; font-display: swap; src: url("/fonts/app-sans-medium.ttf") format("truetype"); }
    @font-face { font-family: "AlterSub Sans"; font-weight: 700; font-display: swap; src: url("/fonts/app-sans-bold.ttf") format("truetype"); }
    :root {
        --bg: #0E0E11; --surface: #18181C; --surface-2: #222228; --line: #2C2C33;
        --text: #F4F4F6; --muted: #9A9AA5; --accent: #FFE500; --ink: #141414;
        --ok: #3DDC84; --danger: #FF6B5E; --radius: 18px;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    html { -webkit-text-size-adjust: 100%; }
    body {
        background: var(--bg); color: var(--text);
        font: 15px/1.4 "AlterSub Sans", system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
        padding: 12px 16px 40px; -webkit-tap-highlight-color: transparent;
    }
    main, .stack { display: grid; gap: 14px; }
    main { max-width: 520px; margin: 0 auto; }
    [hidden] { display: none !important; }

    header { display: flex; align-items: center; justify-content: space-between; padding: 4px 2px; }
    .brand { display: flex; align-items: center; gap: 8px; font-size: 20px; font-weight: 700; letter-spacing: -0.01em; }
    .brand::before { content: ""; width: 10px; height: 10px; border-radius: 50%; background: var(--accent); }
    .pill { display: flex; align-items: center; gap: 6px; padding: 4px 10px; border-radius: 999px; background: var(--surface-2); color: var(--muted); font-size: 12px; font-weight: 500; }
    .pill::before { content: ""; width: 7px; height: 7px; border-radius: 50%; background: currentColor; }
    .pill.ok { color: var(--ok); }
    .pill.bad { color: var(--danger); }

    .card { background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius); padding: 16px; }
    .eyebrow { margin-bottom: 10px; color: var(--muted); font-size: 11px; font-weight: 700; letter-spacing: 0.12em; text-transform: uppercase; }
    .headline { font-size: 22px; font-weight: 700; line-height: 1.25; overflow-wrap: anywhere; }
    .muted { margin-top: 4px; color: var(--muted); overflow-wrap: anywhere; }
    .warning { margin-top: 12px; padding: 10px 12px; border-radius: 12px; background: #3A1A17; color: #FFB4A9; font-size: 13px; }

    button, .button {
        display: inline-flex; align-items: center; justify-content: center; gap: 6px;
        min-height: 44px; padding: 0 14px; border: 1px solid var(--line); border-radius: 12px;
        background: var(--surface-2); color: var(--text); font: inherit; font-weight: 500;
        cursor: pointer; touch-action: manipulation; transition: transform 0.08s, background 0.15s;
    }
    button:active, .button:active { transform: scale(0.96); background: var(--line); }
    button:disabled { opacity: 0.4; }
    .primary { border-color: transparent; background: var(--accent); color: var(--ink); }
    .primary:active { background: #E6CE00; }
    .ghost { background: transparent; }
    .wide { width: 100%; margin-top: 10px; }

    input[type=text] {
        min-height: 44px; padding: 0 12px; border: 1px solid var(--line); border-radius: 12px;
        background: var(--bg); color: var(--text); font: inherit; outline: none;
    }
    input[type=text]:focus { border-color: var(--accent); }
    input[type=file] { display: none; }
    .row { display: flex; align-items: center; gap: 8px; }
    .row > input { flex: 1; min-width: 0; }

    .pin { width: 100%; margin-top: 14px; font-size: 28px; letter-spacing: 0.4em; text-align: center; font-variant-numeric: tabular-nums; }

    .offset { color: var(--accent); font-size: 44px; font-weight: 700; letter-spacing: -0.02em; text-align: center; font-variant-numeric: tabular-nums; }
    .hint { margin: 2px 0 12px; color: var(--muted); font-size: 13px; text-align: center; }
    .grid4 { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
    .clock-row { display: flex; align-items: center; gap: 12px; margin-top: 14px; padding-top: 14px; border-top: 1px solid var(--line); }
    .play { width: 48px; height: 48px; padding: 0; border-radius: 50%; font-size: 16px; }
    .clock { flex: 1; font-variant-numeric: tabular-nums; }
    .clock b { font-size: 18px; }
    .clock span { display: block; color: var(--muted); font-size: 13px; }
    details { margin-top: 10px; }
    summary { padding: 6px 0; color: var(--muted); font-size: 13px; cursor: pointer; list-style: none; }
    summary::-webkit-details-marker { display: none; }
    summary::before { content: "+ "; }
    details[open] summary::before { content: "\2212  "; }

    .tracks { display: grid; gap: 8px; margin-top: 12px; }
    .track { display: flex; align-items: center; gap: 12px; padding: 12px; border: 1px solid transparent; border-radius: 12px; background: var(--surface-2); cursor: pointer; }
    .track .radio { flex: none; width: 18px; height: 18px; border: 2px solid var(--muted); border-radius: 50%; }
    .track.active { border-color: var(--accent); background: #2A2610; }
    .track.active .radio { border-color: var(--accent); background: var(--accent); box-shadow: inset 0 0 0 3px #2A2610; }
    .track-text { flex: 1; min-width: 0; }
    .track-name { font-weight: 500; overflow-wrap: anywhere; }
    .track-meta { margin-top: 2px; color: var(--muted); font-size: 12px; }
    .empty { padding: 14px; color: var(--muted); font-size: 13px; text-align: center; }
    .recent-icon { flex: none; width: 18px; color: var(--accent); font-size: 17px; text-align: center; }
    .subhead { margin: 16px 0 0; color: var(--muted); font-size: 12px; font-weight: 500; }
    .notice { margin-top: 12px; padding: 10px 12px; border-radius: 12px; background: var(--surface-2); font-size: 13px; overflow-wrap: anywhere; }
    .match-icon { flex: none; width: 18px; color: var(--muted); font-size: 15px; text-align: center; }
    .upload { width: 100%; margin-top: 10px; border-style: dashed; color: var(--muted); }

    .setting { display: flex; align-items: center; gap: 10px; }
    .setting + .setting { margin-top: 12px; }
    .setting-label { flex: 1; }
    .setting-label small { display: block; color: var(--muted); font-size: 12px; }
    .stepper { display: flex; }
    .stepper button { width: 52px; padding: 0; border-radius: 0; }
    .stepper button:first-child { border-radius: 12px 0 0 12px; }
    .stepper button:last-child { border-left: none; border-radius: 0 12px 12px 0; }
    .swatches { display: flex; gap: 12px; }
    .swatch { width: 34px; height: 34px; min-height: 0; padding: 0; border: 2px solid rgba(255, 255, 255, 0.15); border-radius: 50%; }
    .swatch.active { outline: 2px solid var(--text); outline-offset: 3px; }
    .danger { color: var(--danger); }

    @media (prefers-reduced-motion: reduce) { * { transition: none !important; } }
</style>
</head>
<body>
<main>
    <header>
        <div class="brand">AlterSub Remote</div>
        <div class="pill" id="conn">Connecting</div>
    </header>

    <section class="card" id="pairCard" hidden>
        <div class="headline">Connect to your TV</div>
        <p class="muted">Scan the QR code on the TV, or enter the 6-digit PIN shown under it. Don't see one? Open AlterSub on the TV.</p>
        <form onsubmit="pair(); return false;">
            <input type="text" id="pinInput" class="pin" inputmode="numeric" autocomplete="one-time-code" maxlength="6" placeholder="------" aria-label="PIN">
            <button class="primary wide" type="submit">Connect</button>
        </form>
        <div class="warning" id="pairError" hidden></div>
    </section>

    <div class="stack" id="remote" hidden>
        <section class="card">
            <div class="eyebrow">Now playing</div>
            <div class="headline" id="detectedTitle">Waiting for something to play</div>
            <div class="muted" id="activeTrackName">No subtitles selected</div>
            <div class="warning" id="overlayWarning" hidden></div>
        </section>

        <section class="card">
            <div class="eyebrow">Timing</div>
            <div class="offset" id="offsetText">+0.00 s</div>
            <div class="hint">Subtitles late? Tap +. Too early? Tap &minus;.</div>
            <div class="grid4">
                <button onclick="adjustOffset(-1000)">&minus;1 s</button>
                <button onclick="adjustOffset(-250)">&minus;0.25</button>
                <button onclick="adjustOffset(250)">+0.25</button>
                <button onclick="adjustOffset(1000)">+1 s</button>
            </div>
            <button class="ghost wide" id="resetOffset" onclick="adjustOffset(-offsetValue)">Reset timing</button>
            <div class="clock-row">
                <button class="play" id="playPauseBtn" onclick="togglePlay()" aria-label="Start or pause the subtitle clock">&#9654;</button>
                <div class="clock"><b id="clockText">0:00:00</b><span id="clockState">Subtitle clock paused</span></div>
            </div>
            <details>
                <summary>Match the player's time</summary>
                <form class="row" style="margin-top: 8px;" onsubmit="seekClock(); return false;">
                    <input type="text" id="seekInput" placeholder="Time shown in the player, e.g. 41:23" aria-label="Player time">
                    <button class="primary" type="submit">Set</button>
                </form>
            </details>
        </section>

        <section class="card">
            <div class="eyebrow">Subtitles</div>
            <form class="row" onsubmit="searchManual(); return false;">
                <input type="text" id="searchInput" enterkeyhint="search" placeholder="Search a movie or show" aria-label="Search">
                <button class="primary" type="submit">Search</button>
            </form>
            <div id="matchBox" hidden></div>
            <div id="recentBox" hidden>
                <div class="subhead">Recent: tap to load again with its timing</div>
                <div class="tracks" id="recentList"></div>
            </div>
            <div class="tracks" id="trackList">
                <div class="empty">No subtitles yet. Search for the movie or show above. Add the year to pick between films with the same name.</div>
            </div>
            <label for="fileUpload" class="button upload">Upload an .srt file from this phone</label>
            <input type="file" id="fileUpload" accept=".srt,.vtt" onchange="uploadFile(this)">
        </section>

        <section class="card">
            <div class="eyebrow">Appearance</div>
            <div class="setting">
                <div class="setting-label">Size<small id="styleSize">&ndash;</small></div>
                <div class="stepper">
                    <button onclick="setStyle('sizeStep=-1')" aria-label="Smaller">A&minus;</button>
                    <button onclick="setStyle('sizeStep=1')" aria-label="Bigger">A+</button>
                </div>
            </div>
            <div class="setting">
                <div class="setting-label">Position<small id="stylePosition">&ndash;</small></div>
                <div class="stepper">
                    <button onclick="setStyle('positionStep=-1')" aria-label="Move up">&#9650;</button>
                    <button onclick="setStyle('positionStep=1')" aria-label="Move down">&#9660;</button>
                </div>
            </div>
            <div class="setting">
                <div class="setting-label">Colour</div>
                <div class="swatches" id="styleColors"></div>
            </div>
            <button class="ghost wide" onclick="setStyle('reset=1')">Reset appearance</button>
        </section>

        <section class="card">
            <div class="eyebrow">This phone</div>
            <p class="muted" style="margin-top: 0;">Paired with the TV. Only one phone can be paired at a time; unpair this one to use another.</p>
            <button class="ghost wide danger" onclick="unpairThisPhone()">Unpair this phone</button>
        </section>
    </div>
</main>

<script>
    let offsetValue = 0;
    let isPlaying = false;
    let lastTracksKey = '';
    let searchStartedAt = 0;

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

    function setText(id, text) {
        const element = document.getElementById(id);
        if (element.textContent !== text) element.textContent = text;
    }

    function setConnection(ok) {
        const pill = document.getElementById('conn');
        pill.textContent = ok ? 'Connected' : 'TV unreachable';
        pill.className = ok ? 'pill ok' : 'pill bad';
    }

    function showPairing(message) {
        // The page itself came from the TV, so it is reachable; it just won't take commands until paired
        const pill = document.getElementById('conn');
        pill.textContent = 'Not paired';
        pill.className = 'pill';
        document.getElementById('remote').hidden = true;
        document.getElementById('pairCard').hidden = false;
        const error = document.getElementById('pairError');
        error.textContent = message || '';
        error.hidden = !message;
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
        let res;
        try {
            res = await fetch(path, request);
        } catch (e) {
            setConnection(false);
            return null;
        }
        if (res.status === 401) {
            saveToken('');
            showPairing();
            return null;
        }
        return res;
    }

    async function pair(pinFromLink) {
        const input = document.getElementById('pinInput');
        const pin = (pinFromLink || input.value).trim();
        if (!pin) return;
        try {
            const res = await fetch('/api/pair?pin=' + encodeURIComponent(pin), { method: 'POST' });
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
            showPairing("Can't reach the TV. Make sure this phone is on the same Wi-Fi.");
            setConnection(false);
        }
    }

    // The TV's QR code carries the PIN in the URL fragment, which is never sent to the server. Read it once,
    // then drop it from the address bar so it isn't kept in history or shared by accident.
    function takePinFromLink() {
        const match = /^#pin=(\d{6})$/.exec(location.hash);
        if (location.hash) history.replaceState(null, '', location.pathname + location.search);
        return match ? match[1] : '';
    }

    async function fetchStatus() {
        if (!token) return; // Not paired; the 2 s poll mustn't wipe a pairing error message
        const res = await api('/api/status', { method: 'GET' });
        if (!res) return;
        let data;
        try { data = await res.json(); } catch (e) { return; }
        setConnection(true);
        showRemote();

        setText('detectedTitle', data.title || 'Waiting for something to play');
        setText('activeTrackName', data.activeTrack ? 'Subtitles: ' + data.activeTrack : 'No subtitles selected');

        const warning = document.getElementById('overlayWarning');
        warning.textContent = data.overlayError || '';
        warning.hidden = !data.overlayError;

        offsetValue = data.offsetMs || 0;
        setText('offsetText', (offsetValue < 0 ? '−' : '+') + (Math.abs(offsetValue) / 1000).toFixed(2) + ' s');
        document.getElementById('resetOffset').disabled = offsetValue === 0;

        isPlaying = !!data.isPlaying;
        setText('playPauseBtn', isPlaying ? '❚❚' : '▶');
        setText('clockText', formatTime(data.positionMs || 0));
        setText('clockState', isPlaying ? 'Subtitle clock running' : 'Subtitle clock paused');

        renderStyle(data.style);
        renderRecent(data.recent || []);
        renderMatches(data);
        renderTracks(data.tracks || [], data.activeTrackId, data.searchState);
    }

    function textElement(tag, className, text) {
        const element = document.createElement(tag);
        element.className = className;
        element.textContent = text;
        return element;
    }

    // Track fields come from uploaders' release names, search queries and scraped screen text, so they are
    // only ever set as text (never parsed as HTML), and each click handler holds its track id directly
    function renderTracks(tracks, activeId, state) {
        const key = JSON.stringify([tracks, activeId, state]);
        if (key === lastTracksKey) return; // Unchanged: don't rebuild the list every poll
        lastTracksKey = key;

        if (tracks.length === 0) {
            const list = document.getElementById('trackList');
            if (state === 'choose' || state === 'not_found') {
                list.textContent = ''; // The match box above explains what to do
            } else {
                const searching = state === 'searching' || Date.now() - searchStartedAt < 3000;
                showTrackMessage(searching ? 'Searching\u2026' : 'No subtitles yet. Search for the movie or show above.');
            }
            return;
        }
        const list = document.getElementById('trackList');
        list.textContent = '';
        for (const t of tracks) {
            const item = document.createElement('div');
            item.className = t.id === activeId ? 'track active' : 'track';
            item.setAttribute('role', 'button');
            item.addEventListener('click', () => selectTrack(t.id));

            const text = document.createElement('div');
            text.className = 'track-text';
            const meta = [t.source, String(t.language || '').toUpperCase()].filter(Boolean).join(' · ');
            text.append(textElement('div', 'track-name', t.title), textElement('div', 'track-meta', meta));
            item.append(textElement('span', 'radio', ''), text);
            list.appendChild(item);
        }
    }

    // Several films can share a title ("Under the Open Sky" 2020 and 2025): ask which one, say when nothing was
    // found, and let a wrong automatic guess be corrected
    let lastMatchesKey = '';
    function renderMatches(data) {
        const state = data.searchState;
        const matches = data.matches || [];
        const key = JSON.stringify([state, matches, data.imdbId, data.title]);
        if (key === lastMatchesKey) return;
        lastMatchesKey = key;

        const box = document.getElementById('matchBox');
        box.textContent = '';
        const others = matches.filter(m => m.imdbId !== data.imdbId);
        let heading = '';
        let options = [];
        let collapsed = false;
        if (state === 'choose') {
            heading = 'Several films match. Which one is it?';
            options = matches;
        } else if (state === 'not_found') {
            heading = 'No English subtitles found for ' + data.title + '. ' +
                (others.length ? 'Is it one of these?' : 'Try another spelling, add the year, or upload a file.');
            options = others;
        } else if (state === 'found' && others.length) {
            collapsed = true;
            options = others;
        }
        box.hidden = !heading && options.length === 0;
        if (box.hidden) return;

        let container = box;
        if (collapsed) {
            container = document.createElement('details');
            container.appendChild(textElement('summary', '', 'Not the right film?'));
            box.appendChild(container);
        } else {
            box.appendChild(textElement('div', 'notice', heading));
        }
        const list = document.createElement('div');
        list.className = 'tracks';
        for (const m of options) {
            const item = document.createElement('div');
            item.className = 'track';
            item.setAttribute('role', 'button');
            item.addEventListener('click', () => chooseMatch(m.imdbId));
            const text = document.createElement('div');
            text.className = 'track-text';
            text.append(textElement('div', 'track-name', m.title));
            item.append(textElement('span', 'match-icon', '\u25B8'), text);
            list.appendChild(item);
        }
        container.appendChild(list);
    }

    async function chooseMatch(imdbId) {
        lastMatchesKey = '';
        document.getElementById('matchBox').hidden = true;
        searchStartedAt = Date.now();
        showTrackMessage('Searching\u2026');
        await api('/api/choose?imdbId=' + encodeURIComponent(imdbId));
        setTimeout(fetchStatus, 1500);
    }

    // Picks remembered on the TV (track + timing per title), newest first, minus what is loaded now
    let lastRecentKey = '';
    function renderRecent(recent) {
        const key = JSON.stringify(recent);
        if (key === lastRecentKey) return;
        lastRecentKey = key;

        document.getElementById('recentBox').hidden = recent.length === 0;
        const list = document.getElementById('recentList');
        list.textContent = '';
        for (const r of recent) {
            const item = document.createElement('div');
            item.className = 'track';
            item.setAttribute('role', 'button');
            item.addEventListener('click', () => restoreRecent(r.key));

            const text = document.createElement('div');
            text.className = 'track-text';
            const timing = r.offsetMs ? ' \u00B7 ' + (r.offsetMs < 0 ? '\u2212' : '+') + (Math.abs(r.offsetMs) / 1000).toFixed(2) + ' s' : '';
            text.append(textElement('div', 'track-name', r.title), textElement('div', 'track-meta', r.track + timing));
            item.append(textElement('span', 'recent-icon', '\u21BA'), text);
            list.appendChild(item);
        }
    }

    async function restoreRecent(key) {
        showTrackMessage('Loading\u2026');
        await api('/api/restore?key=' + encodeURIComponent(key));
        setTimeout(fetchStatus, 800);
    }

    function showTrackMessage(message) {
        lastTracksKey = '';
        const list = document.getElementById('trackList');
        list.textContent = '';
        list.appendChild(textElement('div', 'empty', message));
    }

    async function adjustOffset(delta) {
        if (!delta) return;
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
        setText('styleSize', Math.round(style.textSizeSp) + ' sp');
        setText('stylePosition', Math.round(style.verticalPosition * 100) + '% down the screen');

        // Swatches come from the server's palette, built once with textContent/aria labels (no HTML injection)
        const row = document.getElementById('styleColors');
        if (row.childElementCount === 0) {
            const palette = style.palette || {};
            for (const name of style.colors) {
                const button = document.createElement('button');
                button.className = 'swatch';
                button.dataset.color = name;
                button.setAttribute('aria-label', name);
                button.title = name;
                if (/^#[0-9A-Fa-f]{6}$/.test(palette[name] || '')) button.style.background = palette[name];
                else button.textContent = name.charAt(0).toUpperCase();
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
        const input = document.getElementById('searchInput');
        const query = input.value.trim();
        if (!query) return;
        input.blur(); // Close the phone keyboard so the results are visible
        searchStartedAt = Date.now();
        showTrackMessage('Searching…');
        await api('/api/search?q=' + encodeURIComponent(query));
        setTimeout(fetchStatus, 1500);
    }

    async function uploadFile(input) {
        if (!input.files || input.files.length === 0) return;
        const file = input.files[0];
        const formData = new FormData();
        formData.append('subtitle', file);

        setText('activeTrackName', 'Uploading ' + file.name + '…');
        await api('/api/upload', { body: formData });
        input.value = ''; // Lets the same file be picked again
        setTimeout(fetchStatus, 500);
    }

    async function unpairThisPhone() {
        if (!confirm('Unpair this phone? You can pair it again by scanning the QR code on the TV.')) return;
        await api('/api/unpair');
        saveToken('');
        lastTracksKey = '';
        showPairing('This phone is unpaired. Scan the QR code on the TV to pair it again.');
    }

    async function start() {
        const linkPin = takePinFromLink();
        if (token) {
            showRemote();
            await fetchStatus();
            if (token) return; // Still paired
        }
        if (linkPin) {
            showPairing();
            await pair(linkPin);
        } else {
            showPairing();
        }
    }

    setInterval(fetchStatus, 2000);
    start();
</script>
</body>
</html>
""".trimIndent()
}
