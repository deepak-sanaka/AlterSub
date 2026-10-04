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

    @font-face { font-family: "AlterSub Sans"; font-weight: 400; font-display: swap; src: url("/fonts/app-sans-regular.ttf") format("truetype"); }
    @font-face { font-family: "AlterSub Sans"; font-weight: 500; font-display: swap; src: url("/fonts/app-sans-medium.ttf") format("truetype"); }
    @font-face { font-family: "AlterSub Sans"; font-weight: 700; font-display: swap; src: url("/fonts/app-sans-bold.ttf") format("truetype"); }
    :root {
        --bg: #0E0E11; --surface: #19191E; --surface-2: #24242C; --line: #32323C;
        --text: #F4F4F6; --muted: #A4A4B0; --accent: #FFE500; --ink: #141414;
        --ok: #8EE4AD; --danger: #FFB4A9; --radius: 24px;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    html { -webkit-text-size-adjust: 100%; }
    body { background: var(--bg); color: var(--text); font: 15px/1.45 "AlterSub Sans", system-ui, sans-serif;
        padding: 20px 16px calc(112px + env(safe-area-inset-bottom)); -webkit-tap-highlight-color: transparent; }
    main { max-width: 480px; margin: auto; }
    [hidden] { display: none !important; }
    header { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 24px; }
    .brand { font-size: 19px; font-weight: 700; letter-spacing: -.04em; }
    .pill { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; color: var(--muted); white-space: nowrap; }
    .pill::before { content: ""; width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
    .pill.ok { color: var(--ok); } .pill.bad { color: var(--danger); }
    button, .button { display: inline-flex; align-items: center; justify-content: center; gap: 8px; min-height: 48px;
        padding: 10px 16px; border: 0; border-radius: 16px; background: var(--surface-2); color: var(--text);
        font: inherit; font-weight: 500; cursor: pointer; touch-action: manipulation; }
    button:active, .button:active { opacity: .75; }
    button:disabled { opacity: .45; cursor: default; }
    button:focus-visible, .button:focus-visible, summary:focus-visible, input:focus-visible { outline: 2px solid var(--accent); outline-offset: 4px; }
    .primary { background: var(--accent); color: var(--ink); }
    .ghost { background: transparent; color: var(--muted); }
    .wide { width: 100%; margin-top: 14px; }
    .danger { background: rgba(255, 77, 77, .16); color: var(--danger); }
    .eyebrow { color: var(--muted); font-size: 11px; font-weight: 700; letter-spacing: .12em; text-transform: uppercase; }
    h1 { font-size: 30px; line-height: 1.15; letter-spacing: -.04em; overflow-wrap: anywhere; margin: 8px 0 12px; }
    h2 { font-size: 24px; line-height: 1.2; letter-spacing: -.03em; }
    .muted { color: var(--muted); margin-top: 8px; overflow-wrap: anywhere; }
    .hero { margin-bottom: 28px; }
    .hero-footer { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
    .state { color: var(--muted); font-size: 13px; }
    .state.ready { color: var(--ok); }
    .text-button { min-height: 44px; padding: 4px 0 4px 12px; font-size: 13px; color: var(--accent); background: transparent; }
    .section-head { margin: 0 0 18px; }
    .surface { padding: 22px; border-radius: var(--radius); background: var(--surface); }
    .warning { margin-bottom: 18px; padding: 14px; border-radius: 16px; background: #341D1B; color: var(--danger); font-size: 13px; }
    .row { display: flex; align-items: center; gap: 8px; }
    .row > input { flex: 1; min-width: 0; }
    input[type=text] { min-height: 48px; padding: 12px 14px; border: 1px solid var(--line); border-radius: 16px;
        background: var(--surface); color: var(--text); font: inherit; }
    input[type=text]:focus { border-color: var(--accent); }
    .search { margin-bottom: 12px; } .search input { border-radius: 18px; min-height: 54px; }
    .search button { min-height: 54px; }
    .help { color: var(--muted); font-size: 12px; }
    .search-status { margin-top: 18px; font-size: 14px; color: var(--muted); }
    .search-status:empty { display: none; }
    .selected { margin-top: 20px; padding: 18px; border-radius: 20px; background: #252615; }
    .selected-head { display: flex; align-items: center; gap: 10px; color: var(--accent); font-size: 13px; font-weight: 500; }
    .selected-head svg { width: 22px; height: 22px; }
    .selected-name { font-size: 15px; margin-top: 10px; overflow-wrap: anywhere; }
    .timing-help { margin-top: 20px; text-align: center; }
    .timing-help p { color: var(--muted); font-size: 13px; }
    .timing-help button { margin-top: 10px; }
    details { margin-top: 18px; }
    summary { display: flex; align-items: center; justify-content: space-between; gap: 12px; min-height: 48px;
        color: var(--muted); font-size: 14px; font-weight: 500; cursor: pointer; list-style: none; }
    summary::-webkit-details-marker { display: none; }
    summary::after { content: "+"; font-size: 22px; font-weight: 400; flex: none; }
    details[open] > summary::after { content: "−"; }
    .tracks { display: grid; gap: 8px; margin-top: 10px; }
    .track { display: flex; width: 100%; text-align: left; padding: 14px; gap: 12px; border: 1px solid transparent; border-radius: 18px; }
    .track.active { border-color: var(--accent); background: #252615; }
    .track-text { flex: 1; min-width: 0; }
    .track-name { display: block; font-size: 14px; font-weight: 500; overflow-wrap: anywhere; }
    .track-meta { display: block; margin-top: 3px; font-size: 12px; font-weight: 400; color: var(--muted); overflow-wrap: anywhere; }
    .track-action { flex: none; color: var(--accent); font-size: 12px; }
    .notice { padding: 16px 0 4px; font-size: 15px; }
    .empty { padding: 32px 18px; text-align: center; }
    .empty-icon { display: inline-flex; align-items: center; justify-content: center; width: 64px; height: 64px;
        margin-bottom: 16px; border-radius: 22px; background: var(--surface-2); color: var(--accent); font-size: 24px; font-weight: 700; }
    .empty h3 { font-size: 20px; letter-spacing: -.03em; }
    .empty p { max-width: 270px; margin: 8px auto 0; color: var(--muted); font-size: 14px; }
    .upload { width: 100%; background: transparent; color: var(--muted); margin-top: 18px; border: 1px dashed var(--line); }
    .sync-status { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px;
        padding: 16px 18px 16px 20px; border-radius: var(--radius); background: var(--surface); }
    .sync-status strong { display: block; font-size: 20px; letter-spacing: -.02em; font-variant-numeric: tabular-nums; }
    .sync-status.adjusted strong { color: var(--accent); }
    .sync-status span { display: block; margin-top: 2px; font-size: 12px; color: var(--muted); }
    .card + .card { margin-top: 12px; }
    .card-title { font-size: 17px; font-weight: 700; letter-spacing: -.01em; }
    .card-title small { margin-left: 6px; padding: 2px 8px; border-radius: 999px; background: #343223; color: var(--accent); font-size: 11px; font-weight: 500; vertical-align: 2px; }
    .card-text { margin-top: 6px; color: var(--muted); font-size: 14px; }
    .hear { width: 100%; min-height: 64px; margin-top: 18px; border-radius: 20px; font-size: 17px; font-weight: 700; }
    .hear svg { width: 24px; height: 24px; flex: none; }
    .lines { position: relative; display: grid; gap: 6px; max-height: 50vh; margin: 14px -8px 0; padding: 2px 8px;
        overflow-y: auto; overscroll-behavior: contain; }
    .line { display: block; width: 100%; min-height: 50px; padding: 12px 14px; border-radius: 14px; text-align: left;
        font-size: 15px; font-weight: 400; line-height: 1.35; white-space: pre-line; overflow-wrap: anywhere; }
    .mark { display: flex; align-items: center; gap: 10px; padding: 6px 2px; color: var(--accent); font-size: 12px; font-weight: 500; }
    .mark::before, .mark::after { content: ""; flex: 1; height: 1px; background: currentColor; opacity: .45; }
    .more { min-height: 40px; background: transparent; color: var(--muted); font-size: 13px; }
    .nudge { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-top: 16px; }
    .nudge button { flex-direction: column; gap: 2px; min-height: 80px; border-radius: 18px; }
    .nudge b { font-size: 18px; letter-spacing: -.01em; }
    .nudge small { color: var(--muted); font-size: 12px; font-weight: 400; }
    .steps { display: grid; grid-template-columns: repeat(4, 1fr); gap: 4px; margin-top: 12px; padding: 4px;
        border-radius: 14px; background: var(--bg); }
    .steps button { min-height: 40px; padding: 0; border-radius: 10px; background: transparent; color: var(--muted);
        font-size: 13px; font-variant-numeric: tabular-nums; }
    .steps button[aria-checked=true] { background: var(--surface-2); color: var(--text); }
    .step-label { display: flex; justify-content: space-between; margin-top: 16px; color: var(--muted); font-size: 12px; }
    .clock-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin: 18px 0; }
    .clock b { font-size: 18px; font-variant-numeric: tabular-nums; }
    .clock span { display: block; font-size: 12px; color: var(--muted); }
    .manual { margin-top: 24px; }
    .manual p { font-size: 13px; }
    .preview { display: grid; place-items: center; position: relative; aspect-ratio: 16 / 9; overflow: hidden;
        border-radius: 22px; background: linear-gradient(160deg, #333442, #171821 70%); margin: 20px 0 26px; }
    .preview-label { position: absolute; top: 14px; left: 16px; color: #C0C0CD; font-size: 11px; letter-spacing: .08em; text-transform: uppercase; }
    .sample { position: absolute; left: 16px; right: 16px; bottom: 18%; text-align: center; color: var(--accent);
        font: 700 23px/1.2 Arial, sans-serif; text-shadow: 0 1px 2px #000, 1px 0 2px #000; }
    .setting { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 20px 0; border-bottom: 1px solid var(--line); }
    .setting-label { font-weight: 500; } .setting-label small { display: block; font-weight: 400; color: var(--muted); font-size: 12px; margin-top: 3px; }
    .stepper { display: flex; gap: 6px; } .stepper button { min-width: 44px; padding: 10px 12px; font-size: 13px; }
    .swatches { display: flex; gap: 10px; padding-right: 4px; }
    .swatch { width: 44px; height: 44px; min-height: 44px; padding: 0; border: 5px solid var(--bg); border-radius: 50%; }
    .swatch.active { outline: 2px solid var(--text); outline-offset: 2px; }
    .tabbar { position: fixed; bottom: calc(12px + env(safe-area-inset-bottom)); left: 50%; transform: translateX(-50%);
        display: grid; grid-template-columns: repeat(3, 1fr); width: calc(100% - 32px); max-width: 480px;
        padding: 6px; gap: 4px; border: 1px solid var(--line); border-radius: 24px; background: #202026; z-index: 10; }
    .tabbar button { flex-direction: column; gap: 4px; min-height: 62px; padding: 6px; background: transparent; color: var(--muted); font-size: 11px; border-radius: 18px; }
    .tabbar button[aria-selected=true] { color: var(--accent); background: #343223; }
    .tabbar svg { width: 22px; height: 22px; }
    .pin { width: 100%; margin-top: 22px; font-size: 28px !important; letter-spacing: .25em; text-align: center; font-variant-numeric: tabular-nums; }
    .pair-label { display: block; margin-top: 18px; font-size: 13px; color: var(--muted); }
    .pair .warning { margin: 16px 0 0; }
    .toast { position: fixed; z-index: 20; left: 50%; transform: translateX(-50%); bottom: calc(100px + env(safe-area-inset-bottom));
        width: calc(100% - 48px); max-width: 456px; border-radius: 16px; padding: 14px 18px; background: #34343E; color: var(--text); font-size: 14px; }
    .toast.error { background: #49241E; color: var(--danger); }
    .toast:not([hidden]) { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
    .toast-action { flex: none; min-height: 36px; padding: 6px 4px 6px 12px; background: transparent; color: var(--accent); font-weight: 700; }
    .connection-settings { margin-top: 28px; }
    @media (max-width: 360px) { .brand { font-size: 17px; } h1 { font-size: 27px; } .surface { padding: 18px; } .stepper button { padding: 10px; } }
</style>
</head>
<body>
<main>
    <header>
        <div class="brand">AlterSub Remote</div>
        <div class="pill" id="conn" role="status">Connecting</div>
    </header>
    <div class="warning" id="connectionNotice" role="status" hidden>Reconnecting to your TV. Keep this phone on the same Wi-Fi and leave AlterSub running on the TV.</div>

    <section class="surface pair" id="pairCard" hidden>
        <div class="empty-icon" aria-hidden="true">TV</div>
        <h2>Your TV, connected.</h2>
        <p class="muted">Open AlterSub on your TV and enter the 6-digit PIN. Scanning its QR code connects you automatically.</p>
        <form onsubmit="pair(); return false;">
            <label class="pair-label" for="pinInput">TV pairing code</label>
            <input type="text" id="pinInput" class="pin" inputmode="numeric" autocomplete="one-time-code" maxlength="6" pattern="[0-9]{6}" required placeholder="000000">
            <button class="primary wide" id="pairButton" type="submit">Connect to TV</button>
        </form>
        <div class="warning" id="pairError" role="alert" hidden></div>
    </section>

    <div id="remote" hidden>
        <section class="hero">
            <div class="eyebrow">Watching on your TV</div>
            <h1 id="detectedTitle">What are you watching?</h1>
            <div class="hero-footer">
                <div class="state" id="subtitleState" role="status">Find subtitles to get started</div>
                <button class="text-button" id="changeTitle" onclick="focusSearch()" hidden>Change title</button>
            </div>
            <div class="warning" id="overlayWarning" role="alert" style="margin-top: 16px;" hidden></div>
        </section>

        <section id="subtitlesPanel" role="tabpanel" aria-labelledby="subtitlesTab">
            <form class="row search" onsubmit="searchManual(); return false;">
                <input type="text" id="searchInput" enterkeyhint="search" autocomplete="off" required placeholder="Movie or show title" aria-label="Movie or show title" aria-describedby="searchHelp">
                <button class="primary" id="searchButton" type="submit">Find</button>
            </form>
            <p class="help" id="searchHelp">Add the year if films share a name, e.g. Dune 2021.</p>
            <p class="search-status" id="searchStatus" role="status"></p>
            <div id="matchBox" hidden></div>
            <div class="selected" id="selectedTrack" hidden>
                <div class="selected-head"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="m5 12 4 4L19 6"/></svg>Showing on your TV</div>
                <div class="selected-name" id="activeTrackName"></div>
                <div class="timing-help">
                    <p>Subtitles not lining up with the scene?</p>
                    <button class="primary" onclick="showTab('timing')">Fix subtitle timing &#8594;</button>
                </div>
            </div>
            <details id="trackOptions" hidden>
                <summary id="trackSummary">Choose a subtitle file</summary>
                <div class="tracks" id="trackList"></div>
            </details>
            <div class="empty" id="subtitleEmpty">
                <div class="empty-icon" aria-hidden="true">CC</div>
                <h3>Start with a title</h3>
                <p>Find subtitles for what you’re watching, or use a file from your phone.</p>
            </div>
            <button class="upload" onclick="document.getElementById('fileUpload').click()">+ Use a subtitle file</button>
            <input type="file" id="fileUpload" accept=".srt,.vtt" onchange="uploadFile(this)" hidden>
            <details id="recentBox" hidden>
                <summary>Recently used</summary>
                <p class="help">Your subtitle file and timing are saved together.</p>
                <div class="tracks" id="recentList"></div>
            </details>
            <details class="connection-settings">
                <summary>TV connection</summary>
                <p class="muted">This phone is paired. Disconnect it to pair a different phone.</p>
                <button class="wide danger" onclick="unpairThisPhone()">Disconnect this phone</button>
            </details>
        </section>

        <section id="timingPanel" role="tabpanel" aria-labelledby="timingTab" hidden>
            <div class="empty" id="timingEmpty">
                <div class="empty-icon" aria-hidden="true">CC</div>
                <h3>Choose subtitles first</h3>
                <p>Once a file is ready, you can line up the words with the dialogue here.</p>
                <button class="primary wide" onclick="focusSearch()">Find subtitles</button>
            </div>
            <div id="timingControls" hidden>
                <div class="section-head"><h2>Line up the subtitles</h2><p class="muted">If the words don’t match the voices on your TV, fix it here. It’s saved for this subtitle file.</p></div>
                <div class="sync-status" id="syncStatus" role="status" aria-atomic="true">
                    <div><strong id="offsetText">Original timing</strong><span id="offsetHint">Not adjusted</span></div>
                    <button class="text-button" id="resetOffset" onclick="adjustOffset(-offsetValue)">Reset</button>
                </div>

                <section class="surface card" aria-labelledby="syncTitle">
                    <div id="syncIdle">
                        <h3 class="card-title" id="syncTitle">Sync to a line<small>Easiest</small></h3>
                        <p class="card-text">Tap the moment someone starts speaking on your TV. Then pick the line they said, and the subtitles move to match.</p>
                        <button class="primary hear" id="hearButton" onclick="markLine()"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M4 10v4m4-8v12m4-15v18m4-14v10m4-7v4"/></svg>I hear a line now</button>
                    </div>
                    <div id="syncPick" hidden>
                        <h3 class="card-title">Which line did you hear?</h3>
                        <p class="card-text">No rush: the moment you tapped is saved.</p>
                        <div class="lines" id="lineList"></div>
                        <button class="ghost wide" onclick="closePick()">Cancel</button>
                    </div>
                </section>

                <section class="surface card" aria-labelledby="fineTitle">
                    <h3 class="card-title" id="fineTitle">Fine-tune</h3>
                    <div class="nudge">
                        <button id="earlierButton" onclick="nudge(1)"><b>‹ Earlier</b><small>Words are late</small></button>
                        <button id="laterButton" onclick="nudge(-1)"><b>Later ›</b><small>Words are early</small></button>
                    </div>
                    <div class="step-label"><span>Each tap moves them by</span></div>
                    <div class="steps" id="stepGroup" role="radiogroup" aria-label="Each tap moves the subtitles by"></div>
                </section>

                <details class="manual">
                    <summary>Manual timing controls</summary>
                    <p class="muted">Timing usually follows your TV. Use these if it doesn’t. Your TV remote controls the video; these controls adjust subtitles only.</p>
                    <div class="clock-row">
                        <div class="clock"><b id="clockText">0:00:00</b><span id="clockState">Subtitle timer paused</span></div>
                        <button id="playPauseBtn" onclick="togglePlay()">Resume subtitles</button>
                    </div>
                    <form class="row" onsubmit="seekClock(); return false;">
                        <input type="text" id="seekInput" placeholder="Video time, e.g. 41:23" aria-label="Time shown in the video player" required>
                        <button type="submit">Set time</button>
                    </form>
                </details>
            </div>
        </section>

        <section id="stylePanel" role="tabpanel" aria-labelledby="styleTab" hidden>
            <div class="section-head"><h2>Make them easy to read.</h2><p class="muted">Changes made here apply to the subtitles on your TV.</p></div>
            <div class="preview" aria-label="Approximate subtitle appearance preview">
                <span class="preview-label">Preview</span><div class="sample" id="styleSample">The story starts here.</div>
            </div>
            <div class="setting">
                <div class="setting-label">Text size<small id="styleSize">Medium</small></div>
                <div class="stepper"><button onclick="setStyle('sizeStep=-1')" aria-label="Make subtitles smaller">Smaller</button><button onclick="setStyle('sizeStep=1')" aria-label="Make subtitles larger">Larger</button></div>
            </div>
            <div class="setting">
                <div class="setting-label">Position<small id="stylePosition">Near the bottom</small></div>
                <div class="stepper"><button onclick="setStyle('positionStep=-1')" aria-label="Move subtitles up">Up</button><button onclick="setStyle('positionStep=1')" aria-label="Move subtitles down">Down</button></div>
            </div>
            <div class="setting"><div class="setting-label">Colour<small id="styleColor">Yellow</small></div><div class="swatches" id="styleColors"></div></div>
            <button class="ghost wide" onclick="setStyle('reset=1')">Reset appearance</button>
        </section>
    </div>
</main>
<nav class="tabbar" id="tabbar" role="tablist" aria-label="Remote controls" hidden>
    <button id="subtitlesTab" role="tab" aria-controls="subtitlesPanel" aria-selected="true" onclick="showTab('subtitles')"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><rect x="2.5" y="5" width="19" height="14" rx="3"/><path d="M6 10h5m2 0h5M6 14h3m2 0h7"/></svg>Subtitles</button>
    <button id="timingTab" role="tab" aria-controls="timingPanel" aria-selected="false" tabindex="-1" onclick="showTab('timing')"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>Timing</button>
    <button id="styleTab" role="tab" aria-controls="stylePanel" aria-selected="false" tabindex="-1" onclick="showTab('style')"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="m4 19 6-14 6 14M6 14h8m2 5 3-8 3 8m-5-3h4"/></svg>Style</button>
</nav>
<div class="toast" id="actionNotice" role="status" hidden><span id="noticeText"></span><button class="toast-action" id="noticeAction" type="button" hidden></button></div>

<script>
    let offsetValue = 0;
    let isPlaying = false;
    let lastTracksKey = '';
    let lastActiveId = null;
    let activeTrackId = '';
    let statusSequence = 0;
    let noticeTimeout;

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
        pill.textContent = ok ? 'Connected' : 'Reconnecting';
        pill.className = ok ? 'pill ok' : 'pill bad';
        document.getElementById('connectionNotice').hidden = ok;
    }

    function showTab(name) {
        for (const tab of ['subtitles', 'timing', 'style']) {
            const active = tab === name;
            document.getElementById(tab + 'Panel').hidden = !active;
            const button = document.getElementById(tab + 'Tab');
            button.setAttribute('aria-selected', String(active));
            button.tabIndex = active ? 0 : -1;
        }
        window.scrollTo(0, 0);
    }

    function focusSearch() {
        showTab('subtitles');
        document.getElementById('searchInput').focus();
    }

    // action: optional { label, run } shown as a button in the message, e.g. Undo
    function notify(message, error, action) {
        clearTimeout(noticeTimeout);
        const notice = document.getElementById('actionNotice');
        setText('noticeText', message);
        notice.className = error ? 'toast error' : 'toast';
        const button = document.getElementById('noticeAction');
        button.hidden = !action;
        button.onclick = null;
        if (action) {
            button.textContent = action.label;
            button.onclick = () => { notice.hidden = true; action.run(); };
        }
        notice.hidden = false;
        noticeTimeout = setTimeout(() => { notice.hidden = true; }, error ? 7000 : action ? 8000 : 3500);
    }

    // Sends a command and returns its JSON reply, or null after telling the user what went wrong
    async function request(path, options) {
        const res = await api(path, options);
        if (!res) return null;
        let data = {};
        try { data = await res.json(); } catch (e) {}
        if (!res.ok) {
            notify(data.error || 'That didn’t work. Please try again.', true);
            return null;
        }
        return data;
    }

    async function command(path, options) {
        return (await request(path, options)) !== null;
    }

    // "0.25", "1.5", "12.3": two decimals only where they matter
    function secondsLabel(ms) { const s = Math.abs(ms) / 1000; return s.toFixed(s >= 10 ? 1 : 2).replace(/\.?0+$/, ''); }
    function timingLabel(ms) { return ms ? secondsLabel(ms) + ' s ' + (ms > 0 ? 'earlier' : 'later') : 'Original timing'; }

    function showPairing(message) {
        // The page itself came from the TV, so it is reachable; it just won't take commands until paired
        const pill = document.getElementById('conn');
        pill.textContent = 'Not paired';
        pill.className = 'pill';
        document.getElementById('remote').hidden = true;
        document.getElementById('tabbar').hidden = true;
        document.getElementById('actionNotice').hidden = true;
        document.getElementById('pairCard').hidden = false;
        const error = document.getElementById('pairError');
        error.textContent = message || '';
        error.hidden = !message;
    }

    function showRemote() {
        document.getElementById('pairCard').hidden = true;
        document.getElementById('remote').hidden = false;
        document.getElementById('tabbar').hidden = false;
    }

    // Every API call carries the token; a 401 means this phone isn't paired (or was unpaired on the TV)
    async function api(path, options) {
        if (!token) return null; // The pairing card is already showing
        const request = Object.assign({ method: 'POST' }, options);
        const requestToken = token;
        request.headers = { 'X-AlterSub-Token': requestToken };
        let res;
        try {
            res = await fetch(path, request);
        } catch (e) {
            setConnection(false);
            if (request.method !== 'GET') notify('Couldn’t reach your TV. Please try again when it reconnects.', true);
            return null;
        }
        if (res.status === 401) {
            if (requestToken !== token) return null;
            saveToken('');
            showPairing();
            return null;
        }
        return res;
    }

    async function pair(pinFromLink) {
        const input = document.getElementById('pinInput');
        const pin = (pinFromLink || input.value).trim();
        if (!/^\d{6}$/.test(pin)) { showPairing('Enter the 6-digit code shown on your TV.'); return; }
        const button = document.getElementById('pairButton');
        button.disabled = true;
        button.textContent = 'Connecting…';
        try {
            const res = await fetch('/api/pair?pin=' + encodeURIComponent(pin), { method: 'POST' });
            const data = await res.json();
            if (res.ok && data.token) {
                saveToken(data.token);
                input.value = '';
                showTab('subtitles');
                showRemote();
                fetchStatus();
            } else {
                showPairing(data.error || 'Pairing failed');
            }
        } catch (e) {
            showPairing("Can't reach the TV. Make sure this phone is on the same Wi-Fi.");
            setConnection(false);
        } finally {
            button.disabled = false;
            button.textContent = 'Connect to TV';
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
        if (!token) return;
        const sequence = ++statusSequence;
        const requestToken = token;
        const res = await api('/api/status', { method: 'GET' });
        if (sequence !== statusSequence || requestToken !== token || !res) return;
        if (!res.ok) { setConnection(false); return; }
        let data;
        try { data = await res.json(); } catch (e) { setConnection(false); return; }
        if (sequence !== statusSequence || requestToken !== token) return;
        setConnection(true);
        showRemote();

        setText('detectedTitle', data.title || 'What are you watching?');
        document.getElementById('changeTitle').hidden = !data.title;
        const active = !!data.activeTrackId;
        const state = data.searchState;
        setText('subtitleState', state === 'choose' ? 'Choose the title in Subtitles' : state === 'searching' ? 'Finding subtitles…' :
            active ? (data.isPlaying ? 'Subtitles ready · Playing' : 'Subtitles ready · Paused') : state === 'not_found' ? 'Try another title or a subtitle file' :
            state === 'found' ? 'Choose a file in Subtitles' : 'Find subtitles to get started');
        document.getElementById('subtitleState').classList.toggle('ready', active);
        document.getElementById('selectedTrack').hidden = !active;
        setText('activeTrackName', data.activeTrack || '');
        document.getElementById('timingEmpty').hidden = active;
        document.getElementById('timingControls').hidden = !active;

        const warning = document.getElementById('overlayWarning');
        warning.textContent = data.overlayError || '';
        warning.hidden = !data.overlayError;
        offsetValue = data.offsetMs || 0;
        setText('offsetText', timingLabel(offsetValue));
        setText('offsetHint', offsetValue ? 'Than the original file · saved' : 'Not adjusted');
        document.getElementById('syncStatus').classList.toggle('adjusted', offsetValue !== 0);
        document.getElementById('resetOffset').hidden = offsetValue === 0;
        activeTrackId = data.activeTrackId || '';
        // The lines being chosen from belong to the file that was showing
        if (syncPick && syncPick.trackId !== activeTrackId) closePick();
        isPlaying = !!data.isPlaying;
        setText('playPauseBtn', isPlaying ? 'Pause subtitles' : 'Resume subtitles');
        setText('clockText', formatTime(data.positionMs || 0));
        setText('clockState', isPlaying ? 'Subtitle timer running' : 'Subtitle timer paused');

        renderStyle(data.style);
        renderRecent(data.recent || []);
        renderMatches(data);
        renderTracks(data.tracks || [], data.activeTrackId, state);
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
        if (key === lastTracksKey) return;
        lastTracksKey = key;
        const options = document.getElementById('trackOptions');
        options.hidden = tracks.length === 0;
        document.getElementById('subtitleEmpty').hidden = tracks.length > 0 || state === 'searching' || state === 'choose' || state === 'not_found';
        setText('searchStatus', state === 'searching' ? 'Finding subtitle files…' : state === 'found' && !activeId ? 'Tap a file to use its subtitles.' : '');
        const list = document.getElementById('trackList');
        list.textContent = '';
        if (!tracks.length) return;
        if (lastActiveId !== (activeId || '')) { options.open = !activeId; lastActiveId = activeId || ''; }
        setText('trackSummary', (activeId ? 'Change subtitle file' : 'Choose a subtitle file') + ' · ' + tracks.length);
        const ordered = tracks.filter(t => t.id === activeId).concat(tracks.filter(t => t.id !== activeId));
        for (const t of ordered) {
            const item = document.createElement('button');
            item.type = 'button';
            item.className = t.id === activeId ? 'track active' : 'track';
            item.setAttribute('aria-pressed', String(t.id === activeId));
            item.addEventListener('click', () => selectTrack(t.id));
            const text = document.createElement('span');
            text.className = 'track-text';
            const language = /^(en|eng)$/i.test(t.language || '') ? 'English' : String(t.language || '').toUpperCase();
            const meta = [language, t.source].filter(Boolean).join(' · ');
            text.append(textElement('span', 'track-name', t.title), textElement('span', 'track-meta', meta));
            item.append(text, textElement('span', 'track-action', t.id === activeId ? 'On TV' : 'Use'));
            list.appendChild(item);
        }
    }

    // Catalog choices and release names are rendered as text only.

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
            heading = 'Which title are you watching?';
            options = matches;
        } else if (state === 'not_found') {
            heading = 'No English subtitles found for ' + data.title + '. ' +
                (others.length ? 'Choose another match below, or use a subtitle file.' : 'Try another spelling or use a subtitle file.');
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
            container.appendChild(textElement('summary', '', 'Not the right title?'));
            box.appendChild(container);
        } else {
            box.appendChild(textElement('div', 'notice', heading));
        }
        const list = document.createElement('div');
        list.className = 'tracks';
        for (const m of options) {
            const item = document.createElement('button');
            item.type = 'button';
            item.className = 'track';
            item.addEventListener('click', () => chooseMatch(m.imdbId));
            const text = document.createElement('div');
            text.className = 'track-text';
            text.append(textElement('div', 'track-name', m.title));
            item.append(text, textElement('span', 'track-action', 'Choose'));
            list.appendChild(item);
        }
        container.appendChild(list);
    }

    async function chooseMatch(imdbId) {
        showTrackMessage('Finding subtitles for your choice…');
        if (await command('/api/choose?imdbId=' + encodeURIComponent(imdbId))) {
            lastMatchesKey = '';
            await fetchStatus();
        }
    }

    // Recent choices keep their saved subtitle file and timing.

    let lastRecentKey = '';
    function renderRecent(recent) {
        const key = JSON.stringify(recent);
        if (key === lastRecentKey) return;
        lastRecentKey = key;

        document.getElementById('recentBox').hidden = recent.length === 0;
        const list = document.getElementById('recentList');
        list.textContent = '';
        for (const r of recent) {
            const item = document.createElement('button');
            item.type = 'button';
            item.className = 'track';
            item.addEventListener('click', () => restoreRecent(r.key));

            const text = document.createElement('div');
            text.className = 'track-text';
            const timing = r.offsetMs ? ' · ' + timingLabel(r.offsetMs) : '';
            text.append(textElement('div', 'track-name', r.title), textElement('div', 'track-meta', r.track + timing));
            item.append(text, textElement('span', 'track-action', 'Use again'));
            list.appendChild(item);
        }
    }

    async function restoreRecent(key) {
        showTrackMessage('Loading your saved subtitles…');
        if (await command('/api/restore?key=' + encodeURIComponent(key))) await fetchStatus();
    }

    function showTrackMessage(message) {
        lastTracksKey = '';
        document.getElementById('subtitleEmpty').hidden = true;
        setText('searchStatus', message);
    }

    async function adjustOffset(delta) {
        if (!delta) return;
        if (await command('/api/offset?delta=' + delta)) await fetchStatus();
    }

    // Fine-tune: one step per tap, in the direction the user picks; the step size is remembered on this phone
    const STEPS = [100, 500, 1000, 5000];
    let stepMs = 500;
    try { const saved = Number(localStorage.getItem('altersubStep')); if (STEPS.includes(saved)) stepMs = saved; } catch (e) {}

    function nudge(direction) { adjustOffset(direction * stepMs); }

    function setStep(ms) {
        stepMs = ms;
        try { localStorage.setItem('altersubStep', String(ms)); } catch (e) {}
        renderSteps();
    }

    function renderSteps() {
        const group = document.getElementById('stepGroup');
        if (group.childElementCount === 0) {
            for (const ms of STEPS) {
                const button = textElement('button', '', secondsLabel(ms) + ' s');
                button.type = 'button';
                button.setAttribute('role', 'radio');
                button.dataset.ms = String(ms);
                button.addEventListener('click', () => setStep(ms));
                group.appendChild(button);
            }
            group.addEventListener('keydown', event => {
                const move = { ArrowLeft: -1, ArrowUp: -1, ArrowRight: 1, ArrowDown: 1 }[event.key];
                if (!move) return;
                event.preventDefault();
                const next = STEPS[(STEPS.indexOf(stepMs) + move + STEPS.length) % STEPS.length];
                setStep(next);
                group.querySelector('[data-ms="' + next + '"]').focus();
            });
        }
        for (const button of group.children) {
            const on = Number(button.dataset.ms) === stepMs;
            button.setAttribute('aria-checked', String(on));
            button.tabIndex = on ? 0 : -1;
        }
        const step = secondsLabel(stepMs) + (stepMs === 1000 ? ' second ' : ' seconds ');
        document.getElementById('earlierButton').setAttribute('aria-label', 'Show subtitles ' + step + 'earlier. Use when the words are late.');
        document.getElementById('laterButton').setAttribute('aria-label', 'Show subtitles ' + step + 'later. Use when the words are early.');
    }

    // Sync to a line: the TV saves where the subtitles were when the user heard someone speak, then the user
    // picks which line it was, and the TV moves the subtitles so that line starts at that moment
    let syncPick = null; // { markMs, lines, trackId } while the user is choosing

    async function markLine() {
        const button = document.getElementById('hearButton');
        button.disabled = true;
        const data = await request('/api/sync/mark');
        button.disabled = false;
        if (!data) return;
        if (!data.lines || data.lines.length === 0) {
            notify('This subtitle file has no lines near this point.', true);
            return;
        }
        syncPick = { markMs: data.markMs, lines: data.lines, trackId: activeTrackId };
        document.getElementById('syncIdle').hidden = true;
        document.getElementById('syncPick').hidden = false;
        renderLines();
        const list = document.getElementById('lineList');
        list.scrollTop = document.getElementById('syncMark').offsetTop - list.clientHeight / 2;
    }

    // Lines come from the subtitle file (anyone's upload), so they are only ever set as text
    function renderLines() {
        const list = document.getElementById('lineList');
        list.textContent = '';
        list.appendChild(moreLinesButton('Earlier lines', -1));
        let marked = false;
        const addMark = () => {
            const mark = textElement('div', 'mark', 'Subtitles were here');
            mark.id = 'syncMark';
            list.appendChild(mark);
            marked = true;
        };
        for (const line of syncPick.lines) {
            if (!marked && line.startMs > syncPick.markMs) addMark();
            const item = textElement('button', 'line', line.text);
            item.type = 'button';
            item.addEventListener('click', () => syncToLine(line));
            list.appendChild(item);
        }
        if (!marked) addMark();
        list.appendChild(moreLinesButton('Later lines', 1));
    }

    function moreLinesButton(label, direction) {
        const button = textElement('button', 'more', label);
        button.type = 'button';
        button.addEventListener('click', () => loadMoreLines(direction, button));
        return button;
    }

    async function loadMoreLines(direction, button) {
        const lines = syncPick.lines;
        const edge = direction < 0 ? lines[0] : lines[lines.length - 1];
        const query = direction < 0 ? 'aroundMs=' + (edge.startMs - 1) + '&before=10&after=0' : 'aroundMs=' + edge.startMs + '&before=0&after=10';
        const data = await request('/api/lines?' + query, { method: 'GET' });
        if (!data || !syncPick) return;
        const fresh = (data.lines || []).filter(line => direction < 0 ? line.startMs < edge.startMs : line.startMs > edge.startMs);
        if (fresh.length === 0) {
            button.textContent = direction < 0 ? 'This is the first line' : 'This is the last line';
            button.disabled = true;
            return;
        }
        const list = document.getElementById('lineList');
        const height = list.scrollHeight;
        const top = list.scrollTop;
        syncPick.lines = direction < 0 ? fresh.concat(lines) : lines.concat(fresh);
        renderLines();
        // Keep the lines the user was looking at in place
        list.scrollTop = direction < 0 ? top + list.scrollHeight - height : top;
    }

    async function syncToLine(line) {
        if (!syncPick) return;
        const data = await request('/api/sync/line?markMs=' + syncPick.markMs + '&startMs=' + line.startMs);
        if (!data) return;
        closePick();
        const delta = data.deltaMs || 0;
        if (Math.abs(delta) < 100) notify('Already in sync with that line.');
        else notify('Synced. Subtitles moved ' + timingLabel(delta) + '.', false, { label: 'Undo', run: () => adjustOffset(-delta) });
        await fetchStatus();
    }

    function closePick() {
        syncPick = null;
        document.getElementById('syncPick').hidden = true;
        document.getElementById('syncIdle').hidden = false;
        document.getElementById('lineList').textContent = '';
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
            notify('Enter the video time, for example 41:23 or 1:05:10.', true);
            return;
        }
        if (await command('/api/seek?positionMs=' + ms)) {
            input.value = '';
            notify('Subtitle timer set to ' + formatTime(ms));
            await fetchStatus();
        }
    }

    async function setStyle(params) {
        if (await command('/api/style?' + params)) await fetchStatus();
    }

    function renderStyle(style) {
        if (!style) return;
        setText('styleSize', style.textSizeSp < 26 ? 'Small' : style.textSizeSp < 36 ? 'Medium' : style.textSizeSp < 46 ? 'Large' : 'Extra large');
        setText('stylePosition', style.verticalPosition < .62 ? 'Middle of the screen' : style.verticalPosition < .8 ? 'Below the middle' : 'Near the bottom');
        setText('styleColor', (style.color || '').replace(/^./, c => c.toUpperCase()));
        const sample = document.getElementById('styleSample');
        sample.style.fontSize = Math.max(16, Math.min(32, style.textSizeSp * .7)) + 'px';
        sample.style.bottom = Math.max(10, (1 - style.verticalPosition) * 100) + '%';
        const color = (style.palette || {})[style.color];
        if (/^#[0-9A-Fa-f]{6}$/.test(color || '')) sample.style.color = color;

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
            button.setAttribute('aria-pressed', String(button.dataset.color === style.color));
        }
    }

    async function togglePlay() {
        if (await command('/api/toggle-play')) await fetchStatus();
    }

    async function selectTrack(id) {
        if (await command('/api/select-track?id=' + encodeURIComponent(id))) {
            notify('Loading selected subtitles…');
            await fetchStatus();
        }
    }

    async function searchManual() {
        const input = document.getElementById('searchInput');
        const query = input.value.trim();
        if (!query) return;
        input.blur();
        const button = document.getElementById('searchButton');
        button.disabled = true;
        button.textContent = 'Finding…';
        showTrackMessage('Finding subtitles for ' + query + '…');
        try {
            if (await command('/api/search?q=' + encodeURIComponent(query))) {
                lastMatchesKey = '';
                await fetchStatus();
            } else { lastTracksKey = ''; await fetchStatus(); }
        } finally {
            button.disabled = false;
            button.textContent = 'Find';
        }
    }

    async function uploadFile(input) {
        if (!input.files || !input.files.length) return;
        const file = input.files[0];
        const formData = new FormData();
        formData.append('subtitle', file);
        showTrackMessage('Adding ' + file.name + '…');
        if (await command('/api/upload', { body: formData })) {
            notify('Loading your subtitle file…');
            await fetchStatus();
        }
        input.value = '';
    }

    async function unpairThisPhone() {
        if (!confirm('Disconnect this phone? Scan the QR code on the TV to connect again.')) return;
        if (!await command('/api/unpair')) return;
        saveToken('');
        lastTracksKey = '';
        showPairing('Phone disconnected. Scan the TV’s QR code to reconnect.');
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

    document.getElementById('tabbar').addEventListener('keydown', event => {
        if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
        const tabs = ['subtitles', 'timing', 'style'];
        const current = tabs.findIndex(tab => document.getElementById(tab + 'Tab').getAttribute('aria-selected') === 'true');
        const next = event.key === 'Home' ? 0 : event.key === 'End' ? 2 : (current + (event.key === 'ArrowRight' ? 1 : 2)) % 3;
        event.preventDefault();
        showTab(tabs[next]);
        document.getElementById(tabs[next] + 'Tab').focus();
    });
    renderSteps();
    setInterval(fetchStatus, 2000);
    start();
</script>
</body>
</html>
""".trimIndent()
}
