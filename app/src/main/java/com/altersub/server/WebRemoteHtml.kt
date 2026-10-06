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
<link rel="icon" type="image/png" href="/images/icon.png">
<link rel="apple-touch-icon" href="/images/icon.png">
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
    .brand { display: flex; align-items: center; gap: 10px; font-size: 19px; font-weight: 700; letter-spacing: -.04em; }
    .brand img { display: block; flex: none; height: 48px; width: auto; }
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
    .language { display: inline-flex; align-items: center; gap: 8px; color: var(--muted); font-size: 13px; }
    .language select { min-height: 40px; max-width: 200px; padding: 0 34px 0 12px; border: 1px solid var(--line); border-radius: 12px;
        background: var(--surface) url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%23A4A4B0' stroke-width='2'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E") no-repeat right 10px center / 14px;
        color: var(--text); font: inherit; font-size: 14px; -webkit-appearance: none; appearance: none; }
    .language select:focus { border-color: var(--accent); }
    .search-language { margin-bottom: 8px; }
    .choice { margin-top: 20px; padding: 18px; border-radius: 20px; background: #1D2130; color: #C8D0EA; font-size: 14px; }
    .choice button { margin-top: 12px; }
    .results-button { width: 100%; margin-top: 12px; }
    .selected { margin-top: 20px; padding: 18px; border-radius: 20px; background: #252615; }
    .selected-head { display: flex; align-items: center; gap: 10px; color: var(--accent); font-size: 13px; font-weight: 500; }
    .selected-head svg { width: 22px; height: 22px; }
    .selected-name { font-size: 15px; margin-top: 10px; overflow-wrap: anywhere; }
    .selected-meta { margin-top: 4px; color: var(--muted); font-size: 12px; }
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
    .sync-status strong { display: block; margin-top: 4px; font-size: 30px; line-height: 1.1; letter-spacing: -.03em; font-variant-numeric: tabular-nums; }
    .sync-status.adjusted strong { color: var(--accent); }
    .sync-status .hint { display: block; margin-top: 4px; font-size: 12px; color: var(--muted); }
    .sync-status button { flex: none; min-width: 84px; }
    .tip { display: flex; gap: 10px; margin-bottom: 12px; padding: 14px 16px; border-radius: 18px; background: #1D2130; color: #C8D0EA; font-size: 13px; }
    .tip svg { flex: none; width: 18px; height: 18px; margin-top: 1px; }
    .tip-dismiss { min-height: 36px; margin-top: 10px; padding: 6px 14px; border-radius: 12px; background: rgba(200, 208, 234, .14); color: #E4E9F7; font-size: 13px; }
    .info-anchor { position: relative; }
    .with-info { display: flex; align-items: center; gap: 6px; }
    .info-button { flex: none; width: 36px; min-height: 36px; height: 36px; padding: 0; border-radius: 50%; background: transparent; color: var(--muted); }
    .info-button svg { width: 20px; height: 20px; }
    .info-button[aria-expanded=true] { color: var(--text); background: var(--surface-2); }
    .tooltip { position: absolute; top: 44px; left: 0; right: 0; z-index: 5; padding: 14px 44px 14px 16px; border-radius: 16px;
        background: #2A2F42; color: #DDE3F5; font-size: 13px; box-shadow: 0 12px 32px rgba(0, 0, 0, .5); }
    .tooltip-close { position: absolute; top: 4px; right: 4px; width: 36px; min-height: 36px; height: 36px; padding: 0; border-radius: 50%; background: transparent; color: #DDE3F5; }
    .tooltip-close svg { width: 18px; height: 18px; }
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
    .preview.text-high .preview-label { top: auto; bottom: 14px; }
    .preview-label { position: absolute; top: 14px; left: 16px; color: #C0C0CD; font-size: 11px; letter-spacing: .08em; text-transform: uppercase; }
    .sample { position: absolute; left: 50%; top: 82%; transform: translate(-50%, -50%); white-space: nowrap; text-align: center; font: 700 23px/1.35 Arial, sans-serif; }
    .sample span { padding: 2px 10px; border-radius: 8px; color: var(--accent); background: rgba(0, 0, 0, .7);
        -webkit-box-decoration-break: clone; box-decoration-break: clone; }
    .setting { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 20px 0; border-bottom: 1px solid var(--line); }
    .setting-label { font-weight: 500; } .setting-label small { display: block; font-weight: 400; color: var(--muted); font-size: 12px; margin-top: 3px; }
    .stepper { display: flex; gap: 6px; } .stepper button { min-width: 44px; padding: 10px 12px; font-size: 13px; }
    .swatches { display: flex; gap: 10px; padding-right: 4px; }
    .swatch { width: 44px; height: 44px; min-height: 44px; padding: 0; border: 5px solid var(--bg); border-radius: 50%; }
    .swatch.active { outline: 2px solid var(--text); outline-offset: 2px; }
    .setting.stacked { display: grid; justify-content: stretch; gap: 14px; }
    .axis { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
    .axis-label { font-size: 14px; color: var(--text); } .axis-label small { display: block; color: var(--muted); font-size: 12px; margin-top: 2px; }
    .backgrounds { display: grid; grid-template-columns: repeat(5, 1fr); gap: 8px; }
    .background-option { min-height: 52px; padding: 0; border-radius: 12px; background: linear-gradient(160deg, #565868, #171821 80%);
        font: 700 15px/1.3 Arial, sans-serif; }
    .background-option span { padding: 1px 6px; border-radius: 4px; }
    .background-option.active { outline: 2px solid var(--text); outline-offset: 2px; }
    .tabbar { position: fixed; bottom: calc(12px + env(safe-area-inset-bottom)); left: 50%; transform: translateX(-50%);
        display: grid; grid-template-columns: repeat(3, 1fr); width: calc(100% - 32px); max-width: 480px;
        padding: 6px; gap: 4px; border: 1px solid var(--line); border-radius: 24px; background: #202026; z-index: 10; }
    .tabbar button { flex-direction: column; gap: 4px; min-height: 62px; padding: 6px; background: transparent; color: var(--muted); font-size: 11px; border-radius: 18px; }
    .tabbar button[aria-selected=true] { color: var(--accent); background: #343223; }
    .tabbar svg { width: 22px; height: 22px; }
    .pin { width: 100%; margin-top: 22px; font-size: 28px !important; letter-spacing: .25em; text-align: center; font-variant-numeric: tabular-nums; }
    .pair-label { display: block; margin-top: 18px; font-size: 13px; color: var(--muted); }
    .pair .warning { margin: 16px 0 0; }
    .toast { position: fixed; z-index: 40; left: 50%; transform: translateX(-50%); bottom: calc(100px + env(safe-area-inset-bottom));
        width: calc(100% - 48px); max-width: 456px; border-radius: 16px; padding: 14px 18px; background: #34343E; color: var(--text); font-size: 14px; }
    .toast.error { background: #49241E; color: var(--danger); }
    .toast:not([hidden]) { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
    .toast-action { flex: none; min-height: 36px; padding: 6px 4px 6px 12px; background: transparent; color: var(--accent); font-weight: 700; }
    .connection-settings { margin-top: 28px; }

    /* File picker: a sheet over the page, grouped by title */
    body.sheet-open { overflow: hidden; }
    .sheet-backdrop { position: fixed; inset: 0; z-index: 30; display: flex; align-items: flex-end; justify-content: center; background: rgba(0, 0, 0, .62); }
    .sheet { display: flex; flex-direction: column; width: 100%; max-width: 520px; max-height: calc(100% - 24px);
        border-radius: 28px 28px 0 0; background: var(--bg); box-shadow: 0 -1px 0 var(--line); }
    .sheet-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; padding: 20px 20px 10px; }
    .sheet-head h2 { margin-top: 4px; font-size: 22px; overflow-wrap: anywhere; }
    .icon-button { flex: none; width: 44px; height: 44px; min-height: 44px; padding: 0; border-radius: 50%; font-size: 18px; }
    .sheet-tools { padding: 0 20px 14px; border-bottom: 1px solid var(--line); }
    .sheet-body { flex: 1; overflow-y: auto; overscroll-behavior: contain; padding: 4px 20px calc(24px + env(safe-area-inset-bottom)); }
    .loading { display: flex; flex-direction: column; align-items: center; gap: 16px; padding: 56px 0; color: var(--muted); font-size: 14px; text-align: center; }
    .spinner { width: 44px; height: 44px; border: 4px solid var(--surface-2); border-top-color: var(--accent); border-radius: 50%; animation: spin .8s linear infinite; }
    @keyframes spin { to { transform: rotate(360deg); } }
    @media (prefers-reduced-motion: reduce) { .spinner, .duration.checking::before { animation-duration: 2.4s; } }
    .choose-hint { margin-top: 12px; padding: 12px 14px; border-radius: 14px; background: #1D2130; color: #C8D0EA; font-size: 13px; }
    .title-group { padding: 18px 0 4px; }
    .title-group + .title-group { margin-top: 8px; border-top: 1px solid var(--line); }
    .group-title { font-size: 18px; letter-spacing: -.02em; overflow-wrap: anywhere; }
    .group-meta { margin-top: 2px; color: var(--muted); font-size: 13px; }
    .files { display: grid; gap: 8px; margin-top: 12px; }
    .file { display: block; width: 100%; padding: 14px 16px; border: 1px solid transparent; border-radius: 18px; background: var(--surface);
        font-weight: 400; text-align: left; }
    .file.active { border-color: var(--accent); background: #252615; }
    .file-top { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
    .file-name { font-size: 14px; font-weight: 500; line-height: 1.35; overflow-wrap: anywhere; }
    .file-action { flex: none; color: var(--accent); font-size: 13px; font-weight: 700; }
    .file-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 10px; margin-top: 8px; color: var(--muted); font-size: 12px; }
    .tag { padding: 2px 8px; border-radius: 999px; background: var(--surface-2); color: var(--text); }
    .tag.last { background: #343223; color: var(--accent); }
    .duration.checking::before { content: ""; display: inline-block; width: 9px; height: 9px; margin-right: 6px; vertical-align: -1px;
        border: 2px solid var(--surface-2); border-top-color: var(--muted); border-radius: 50%; animation: spin .8s linear infinite; }
    .sheet-empty { padding: 40px 4px; text-align: center; }
    .sheet-empty h3 { font-size: 18px; overflow-wrap: anywhere; }
    .sheet-empty p { max-width: 310px; margin: 8px auto 0; color: var(--muted); font-size: 14px; }
    @media (max-width: 360px) { .brand { font-size: 17px; } h1 { font-size: 27px; } .surface { padding: 18px; } .stepper button { padding: 10px; } }
</style>
</head>
<body>
<main>
    <header>
        <div class="brand"><img src="/images/logo.png" width="74" height="48" alt="AlterSub">Remote</div>
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
            <div class="search-language"><label class="language">Subtitles in <select id="languageSelect" aria-label="Subtitle language" onchange="setLanguage(this.value)"></select></label></div>
            <p class="help" id="searchHelp">Add the year if films share a name, e.g. Dune 2021.</p>
            <div class="choice" id="choiceNotice" role="status" hidden>
                Several films share this title. Pick the subtitle file for the one you’re watching.
                <button class="primary wide" onclick="openResults()">Choose the film</button>
            </div>
            <div class="selected" id="selectedTrack" hidden>
                <div class="selected-head"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="m5 12 4 4L19 6"/></svg>Showing on your TV</div>
                <div class="selected-name" id="activeTrackName"></div>
                <div class="selected-meta" id="activeTrackMeta"></div>
                <div class="timing-help">
                    <p>Subtitles not lining up with the scene?</p>
                    <button class="primary" onclick="showTab('timing')">Fix subtitle timing &#8594;</button>
                </div>
            </div>
            <button class="results-button" id="resultsButton" onclick="openResults()" hidden>Choose another file</button>
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
                <div class="section-head info-anchor">
                    <h2 class="with-info">Line up the subtitles<button class="info-button" id="timingInfoButton" onclick="toggleTimingInfo()" aria-label="Timing tip" aria-expanded="false" aria-controls="timingInfo" hidden><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 11v5m0-8.5v.5"/></svg></button></h2>
                    <div class="tooltip" id="timingInfo" role="dialog" aria-label="Timing tip" hidden>
                        <p>Easiest near the start of a film or episode: the first time someone speaks is simple to match with the first subtitle. Once set, the timing is saved for this subtitle file.</p>
                        <button class="tooltip-close" onclick="closeTimingInfo()" aria-label="Close tip"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg></button>
                    </div>
                    <p class="muted">If the words don’t match the voices on your TV, adjust them here.</p>
                </div>
                <div class="tip" id="timingTip"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 11v5m0-8.5v.5"/></svg><div><p>Easiest near the start of a film or episode: the first time someone speaks is simple to match with the first subtitle. Once set, the timing is saved for this subtitle file.</p><button class="tip-dismiss" onclick="dismissTimingTip()">OK, understood</button></div></div>
                <div class="sync-status" id="syncStatus">
                    <div role="status" aria-atomic="true"><span class="eyebrow">Subtitle timing</span><strong id="offsetText">0 s</strong><span class="hint" id="offsetHint">Original timing</span></div>
                    <button id="resetOffset" onclick="adjustOffset(-offsetValue)" aria-label="Reset subtitle timing to 0 seconds">Reset</button>
                </div>

                <section class="surface card" aria-labelledby="fineTitle">
                    <h3 class="card-title" id="fineTitle">Adjust timing</h3>
                    <p class="card-text">Watch someone speak and compare it with when their subtitle appears.</p>
                    <div class="nudge">
                        <button id="earlierButton" onclick="nudge(1)"><b id="earlierStep">−0.5 s</b><small>Words appear late</small></button>
                        <button id="laterButton" onclick="nudge(-1)"><b id="laterStep">+0.5 s</b><small>Words appear early</small></button>
                    </div>
                    <div class="step-label"><span>Each tap moves them by</span></div>
                    <div class="steps" id="stepGroup" role="radiogroup" aria-label="Each tap moves the subtitles by"></div>
                </section>

                <section class="surface card" aria-labelledby="syncTitle">
                    <div id="syncIdle">
                        <h3 class="card-title" id="syncTitle">Sync to a line</h3>
                        <p class="card-text">If you understand the language being spoken: tap the moment someone starts speaking, then pick the line they said. The subtitles move to match.</p>
                        <button class="primary hear" id="hearButton" onclick="markLine()"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M4 10v4m4-8v12m4-15v18m4-14v10m4-7v4"/></svg>I hear a line now</button>
                    </div>
                    <div id="syncPick" hidden>
                        <h3 class="card-title">Which line did you hear?</h3>
                        <p class="card-text">No rush: the moment you tapped is saved.</p>
                        <div class="lines" id="lineList"></div>
                        <button class="ghost wide" onclick="closePick()">Cancel</button>
                    </div>
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
                <span class="preview-label">Preview</span><div class="sample" id="styleSample"><span id="styleSampleText">The story starts here.</span></div>
            </div>
            <div class="setting">
                <div class="setting-label">Text size<small id="styleSize">Medium</small></div>
                <div class="stepper"><button onclick="setStyle('sizeStep=-1')" aria-label="Make subtitles smaller">Smaller</button><button onclick="setStyle('sizeStep=1')" aria-label="Make subtitles larger">Larger</button></div>
            </div>
            <div class="setting stacked">
                <div class="setting-label">Position</div>
                <div class="axis">
                    <div class="axis-label">Vertical<small id="stylePosition">Near the bottom</small></div>
                    <div class="stepper"><button onclick="setStyle('positionStep=-1')" aria-label="Move subtitles up">Up</button><button onclick="setStyle('positionStep=1')" aria-label="Move subtitles down">Down</button></div>
                </div>
                <div class="axis">
                    <div class="axis-label">Horizontal<small id="styleSide">Centred</small></div>
                    <div class="stepper"><button onclick="setStyle('horizontalStep=-1')" aria-label="Move subtitles left">Left</button><button onclick="setStyle('horizontalStep=1')" aria-label="Move subtitles right">Right</button></div>
                </div>
            </div>
            <div class="setting stacked"><div class="setting-label">Background<small id="styleBackground">See-through black</small></div><div class="backgrounds" id="styleBackgrounds" role="group" aria-label="Background"></div></div>
            <div class="setting"><div class="setting-label">Text colour<small id="styleColor">Yellow</small></div><div class="swatches" id="styleColors"></div></div>
            <button class="ghost wide" onclick="setStyle('reset=1')">Reset appearance</button>
        </section>
    </div>
</main>
<div class="sheet-backdrop" id="resultsSheet" hidden onclick="if (event.target === this) closeResults()">
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="sheetTitle">
        <div class="sheet-head">
            <div><p class="eyebrow">Subtitle files for</p><h2 id="sheetTitle">Your search</h2></div>
            <button class="icon-button" id="sheetClose" type="button" aria-label="Close" onclick="closeResults()">✕</button>
        </div>
        <div class="sheet-tools">
            <label class="language">Subtitles in <select id="sheetLanguage" aria-label="Subtitle language" onchange="setLanguage(this.value)"></select></label>
            <p class="choose-hint" id="sheetChoose" hidden>Several films share this title. Pick the file for the one you’re watching.</p>
        </div>
        <div class="sheet-body">
            <div class="loading" id="sheetLoading" role="status"><span class="spinner" aria-hidden="true"></span><span id="sheetLoadingText">Finding subtitles…</span></div>
            <div class="sheet-empty" id="sheetEmpty" hidden>
                <h3 id="sheetEmptyTitle">No subtitles found</h3>
                <p>Try another language, add the year, or check the spelling. You can also use a subtitle file from your phone.</p>
                <button class="wide" onclick="closeResults(); document.getElementById('fileUpload').click()">Use a subtitle file</button>
            </div>
            <div id="sheetGroups"></div>
        </div>
    </div>
</div>
<nav class="tabbar" id="tabbar" role="tablist" aria-label="Remote controls" hidden>
    <button id="subtitlesTab" role="tab" aria-controls="subtitlesPanel" aria-selected="true" onclick="showTab('subtitles')"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><rect x="2.5" y="5" width="19" height="14" rx="3"/><path d="M6 10h5m2 0h5M6 14h3m2 0h7"/></svg>Subtitles</button>
    <button id="timingTab" role="tab" aria-controls="timingPanel" aria-selected="false" tabindex="-1" onclick="showTab('timing')"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>Timing</button>
    <button id="styleTab" role="tab" aria-controls="stylePanel" aria-selected="false" tabindex="-1" onclick="showTab('style')"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="m4 19 6-14 6 14M6 14h8m2 5 3-8 3 8m-5-3h4"/></svg>Style</button>
</nav>
<div class="toast" id="actionNotice" role="status" hidden><span id="noticeText"></span><button class="toast-action" id="noticeAction" type="button" hidden></button></div>

<script>
    let offsetValue = 0;
    let isPlaying = false;
    let languagesShown = false;
    let currentLanguage = 'en';
    let languageNames = {};
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
        // The preview can only be measured once its tab shows
        if (name === 'style' && lastStyle) renderStyle(lastStyle);
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

    // "0.25", "1.5", "12.3": two decimals only under a second
    function secondsLabel(ms) { const s = Math.abs(ms) / 1000; return s.toFixed(s >= 1 ? 1 : 2).replace(/\.?0+$/, ''); }
    // Timing is shown as a delay: + shows subtitles later, − sooner, matching the +/− buttons. (The clock's offset
    // has the opposite sign: it is added to the cue time, so a positive offset shows them sooner.)
    function timingLabel(offsetMs) { return offsetMs ? (offsetMs < 0 ? '+' : '−') + secondsLabel(offsetMs) + ' s' : '0 s'; }

    function showPairing(message) {
        // The page itself came from the TV, so it is reachable; it just won't take commands until paired
        const pill = document.getElementById('conn');
        pill.textContent = 'Not paired';
        pill.className = 'pill';
        document.getElementById('remote').hidden = true;
        document.getElementById('tabbar').hidden = true;
        document.getElementById('actionNotice').hidden = true;
        closeResults();
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
        const resultsCount = data.resultsCount || 0;
        setText('subtitleState', state === 'choose' ? 'Pick the film in Subtitles' : state === 'searching' ? 'Finding subtitles…' :
            active ? (data.isPlaying ? 'Subtitles ready · Playing' : 'Subtitles ready · Paused') : state === 'not_found' ? 'No subtitles found yet' :
            resultsCount ? 'Pick a subtitle file' : 'Find subtitles to get started');
        document.getElementById('subtitleState').classList.toggle('ready', active);
        document.getElementById('selectedTrack').hidden = !active;
        setText('activeTrackName', data.activeTrack || '');
        setText('activeTrackMeta', data.activeTrackLanguage || '');
        document.getElementById('choiceNotice').hidden = state !== 'choose';
        const resultsButton = document.getElementById('resultsButton');
        resultsButton.hidden = !resultsCount || state === 'choose';
        setText('resultsButton', active ? 'Choose another file' : 'See ' + resultsCount + (resultsCount === 1 ? ' subtitle file' : ' subtitle files'));
        document.getElementById('subtitleEmpty').hidden = active || resultsCount > 0 || state === 'searching' || state === 'choose';
        renderLanguages(data.languages || [], data.language || 'en');
        document.getElementById('timingEmpty').hidden = active;
        document.getElementById('timingControls').hidden = !active;

        const warning = document.getElementById('overlayWarning');
        warning.textContent = data.overlayError || '';
        warning.hidden = !data.overlayError;
        offsetValue = data.offsetMs || 0;
        setText('offsetText', timingLabel(offsetValue));
        setText('offsetHint', offsetValue ? 'Saved for this subtitle file' : 'Original timing');
        document.getElementById('syncStatus').classList.toggle('adjusted', offsetValue !== 0);
        document.getElementById('resetOffset').disabled = offsetValue === 0;
        activeTrackId = data.activeTrackId || '';
        // The lines being chosen from belong to the file that was showing
        if (syncPick && syncPick.trackId !== activeTrackId) closePick();
        isPlaying = !!data.isPlaying;
        setText('playPauseBtn', isPlaying ? 'Pause subtitles' : 'Resume subtitles');
        setText('clockText', formatTime(data.positionMs || 0));
        setText('clockState', isPlaying ? 'Subtitle timer running' : 'Subtitle timer paused');

        renderStyle(data.style);
        renderRecent(data.recent || []);
    }

    function textElement(tag, className, text) {
        const element = document.createElement(tag);
        element.className = className;
        element.textContent = text;
        return element;
    }

    // The language picker, on the Subtitles tab and in the file sheet. Built once from the TV's list.
    function renderLanguages(languages, current) {
        const selects = [document.getElementById('languageSelect'), document.getElementById('sheetLanguage')];
        if (!languagesShown && languages.length) {
            for (const select of selects) {
                for (const language of languages) {
                    const label = language.nativeName && language.nativeName !== language.name ? language.name + ' · ' + language.nativeName : language.name;
                    const option = textElement('option', '', label);
                    option.value = language.code;
                    select.appendChild(option);
                }
            }
            languageNames = {};
            for (const language of languages) languageNames[language.code] = language.name;
            languagesShown = true;
        }
        currentLanguage = current;
        for (const select of selects) if (document.activeElement !== select) select.value = current;
    }

    async function setLanguage(code) {
        if (code === currentLanguage) return;
        const previous = currentLanguage;
        if (await command('/api/language?code=' + encodeURIComponent(code))) {
            currentLanguage = code;
            for (const id of ['languageSelect', 'sheetLanguage']) document.getElementById(id).value = code;
            notify('Subtitles in ' + (languageNames[code] || code) + ' from now on.');
            if (sheetOpen) { lastResultsKey = ''; await fetchResults(); }
            await fetchStatus();
        } else {
            for (const id of ['languageSelect', 'sheetLanguage']) document.getElementById(id).value = previous;
        }
    }

    // The file sheet. Polls the TV while open: results arrive after the search, then each file's length.
    // File names, release names and titles come from uploaders and catalogs, so they are only ever set as text.
    let sheetOpen = false;
    let sheetTimer = null;
    let sheetOpener = null;
    let lastResultsKey = '';

    function openResults(searchingFor) {
        sheetOpener = document.activeElement;
        sheetOpen = true;
        lastResultsKey = '';
        document.getElementById('resultsSheet').hidden = false;
        document.body.classList.add('sheet-open');
        document.getElementById('sheetClose').focus();
        if (searchingFor) {
            // Show the spinner at once; polling starts when the TV has the search
            renderResults({ state: 'searching', query: searchingFor, languageName: languageNames[currentLanguage] || '', groups: [] });
        } else {
            startResultsPolling();
        }
    }

    function startResultsPolling() {
        clearInterval(sheetTimer);
        sheetTimer = setInterval(fetchResults, 1000);
        fetchResults();
    }

    function closeResults() {
        if (!sheetOpen) return;
        sheetOpen = false;
        clearInterval(sheetTimer);
        document.getElementById('resultsSheet').hidden = true;
        document.body.classList.remove('sheet-open');
        if (sheetOpener && document.body.contains(sheetOpener)) sheetOpener.focus();
    }

    async function fetchResults() {
        if (!sheetOpen) return;
        const res = await api('/api/results', { method: 'GET' });
        if (!res || !res.ok || !sheetOpen) return;
        let data;
        try { data = await res.json(); } catch (e) { return; }
        if (sheetOpen) renderResults(data);
    }

    function renderResults(data) {
        const key = JSON.stringify(data);
        if (key === lastResultsKey) return;
        lastResultsKey = key;
        const groups = data.groups || [];
        const languageName = data.languageName || '';
        const searching = data.state === 'searching';
        setText('sheetTitle', data.query ? '“' + data.query + '”' : 'Subtitle files');
        document.getElementById('sheetLoading').hidden = !searching;
        setText('sheetLoadingText', 'Finding ' + (languageName ? languageName + ' ' : '') + 'subtitles…');
        document.getElementById('sheetChoose').hidden = searching || data.state !== 'choose';
        const empty = !searching && groups.length === 0;
        document.getElementById('sheetEmpty').hidden = !empty;
        setText('sheetEmptyTitle', data.query ? 'No ' + (languageName ? languageName + ' ' : '') + 'subtitles for “' + data.query + '”' : 'Search for a title to see its subtitle files');

        const container = document.getElementById('sheetGroups');
        container.textContent = '';
        if (searching) return;
        for (const group of groups) container.appendChild(groupElement(group));
    }

    function groupElement(group) {
        const section = document.createElement('section');
        section.className = 'title-group';
        section.appendChild(textElement('h3', 'group-title', group.title));
        const facts = [group.episode, group.year, countryLabel(group.country), group.runtimeMinutes ? runtimeLabel(group.runtimeMinutes) : '',
            group.files.length + (group.files.length === 1 ? ' file' : ' files')].filter(Boolean);
        section.appendChild(textElement('p', 'group-meta', facts.join(' · ')));
        const files = document.createElement('div');
        files.className = 'files';
        for (const file of group.files) files.appendChild(fileElement(file));
        section.appendChild(files);
        return section;
    }

    function fileElement(file) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = file.active ? 'file active' : 'file';
        button.setAttribute('aria-pressed', String(!!file.active));
        button.addEventListener('click', () => useResult(file.id, file.active));
        const top = document.createElement('span');
        top.className = 'file-top';
        top.append(textElement('span', 'file-name', file.fileName), textElement('span', 'file-action', file.active ? 'On TV' : 'Use'));
        const meta = document.createElement('span');
        meta.className = 'file-meta';
        meta.appendChild(textElement('span', 'tag', file.language));
        if (file.release) meta.appendChild(textElement('span', '', file.release));
        meta.appendChild(durationElement(file.durationMs));
        if (file.lastUsed && !file.active) meta.appendChild(textElement('span', 'tag last', 'Last used'));
        button.append(top, meta);
        return button;
    }

    // How long the file runs, to compare with the film's runtime: the right cut ends just before the credits
    function durationElement(ms) {
        if (ms === null || ms === undefined) return textElement('span', 'duration checking', 'Checking length');
        if (ms < 0) return textElement('span', 'duration', 'Length unknown');
        return textElement('span', 'duration', 'Runs ' + formatTime(ms));
    }

    // Co-productions list many countries: "United States, Canada +6"
    function countryLabel(country) {
        const countries = String(country || '').split(',').map(name => name.trim()).filter(Boolean);
        return countries.length > 2 ? countries.slice(0, 2).join(', ') + ' +' + (countries.length - 2) : countries.join(', ');
    }

    function runtimeLabel(minutes) {
        const hours = Math.floor(minutes / 60);
        const rest = minutes % 60;
        return hours ? hours + ' h' + (rest ? ' ' + rest + ' min' : '') : rest + ' min';
    }

    async function useResult(id, alreadyOn) {
        if (alreadyOn) { closeResults(); return; }
        if (await command('/api/use?id=' + encodeURIComponent(id))) {
            closeResults();
            notify('Loading subtitles on your TV…');
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
            const timing = r.offsetMs ? ' · timing ' + timingLabel(r.offsetMs) : '';
            text.append(textElement('div', 'track-name', r.title), textElement('div', 'track-meta', r.track + timing));
            item.append(text, textElement('span', 'track-action', 'Use again'));
            list.appendChild(item);
        }
    }

    async function restoreRecent(key) {
        if (await command('/api/restore?key=' + encodeURIComponent(key))) {
            notify('Loading your saved subtitles…');
            await fetchStatus();
        }
    }

    async function adjustOffset(delta) {
        if (!delta) return;
        if (await command('/api/offset?delta=' + delta)) await fetchStatus();
    }

    // Fine-tune: one step per tap, in the direction the user picks; the step size is remembered on this phone
    const STEPS = [250, 500, 1000, 5000];
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
        setText('earlierStep', '−' + secondsLabel(stepMs) + ' s');
        setText('laterStep', '+' + secondsLabel(stepMs) + ' s');
        const step = secondsLabel(stepMs) + (stepMs === 1000 ? ' second' : ' seconds');
        document.getElementById('earlierButton').setAttribute('aria-label', 'Minus ' + step + ': show subtitles sooner. Use when the words appear late.');
        document.getElementById('laterButton').setAttribute('aria-label', 'Plus ' + step + ': show subtitles later. Use when the words appear early.');
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
        else notify('Synced. Subtitle timing is now ' + timingLabel(data.offsetMs || 0) + '.', false, { label: 'Undo', run: () => adjustOffset(-delta) });
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

    const HEX_COLOR = /^#[0-9A-Fa-f]{6}$/;
    const BACKGROUND_NAMES = { 'translucent-black': 'See-through black', black: 'Solid black',
        'translucent-white': 'See-through white', white: 'Solid white', none: 'No background' };

    // How a background from the server looks with the chosen text colour: CSS for the box, text and outline
    function backgroundLook(background, textColor) {
        let box = 'transparent';
        if (HEX_COLOR.test(background.box || '') && typeof background.boxOpacity === 'number') {
            const n = parseInt(background.box.slice(1), 16);
            box = 'rgba(' + (n >> 16) + ',' + ((n >> 8) & 255) + ',' + (n & 255) + ',' + background.boxOpacity + ')';
        }
        const edge = HEX_COLOR.test(background.edge || '') ? background.edge : '#000000';
        return {
            box: box,
            text: HEX_COLOR.test(background.text || '') ? background.text : textColor,
            outline: [[-1, -1], [1, -1], [-1, 1], [1, 1]].map(([x, y]) => x + 'px ' + y + 'px 0 ' + edge).join(', ') + ', 0 0 3px ' + edge
        };
    }

    function applyLook(element, look) {
        element.style.background = look.box;
        element.style.color = look.text;
        element.style.textShadow = look.outline;
    }

    // Centres the preview text where the TV puts it, kept inside the frame the way the TV keeps it on screen
    var lastStyle = null; // var: showTab can run before this line does

    function placeSample(v, h) {
        const sample = document.getElementById('styleSample'), frame = sample.parentElement;
        if (!frame.clientWidth) return;
        const clamp = (value, min, max) => max < min ? (min + max) / 2 : Math.min(max, Math.max(min, value));
        const margin = .03;
        const x = clamp(frame.clientWidth * h, frame.clientWidth * margin + sample.offsetWidth / 2, frame.clientWidth * (1 - margin) - sample.offsetWidth / 2);
        const y = clamp(frame.clientHeight * v, frame.clientHeight * margin + sample.offsetHeight / 2, frame.clientHeight * (1 - margin) - sample.offsetHeight / 2);
        sample.style.left = x + 'px';
        sample.style.top = y + 'px';
        // Out of the text's way
        frame.classList.toggle('text-high', v < .4);
    }

    function renderStyle(style) {
        if (!style) return;
        lastStyle = style;
        setText('styleSize', style.textSizeSp < 26 ? 'Small' : style.textSizeSp < 36 ? 'Medium' : style.textSizeSp < 46 ? 'Large' : 'Extra large');
        const v = style.verticalPosition, h = typeof style.horizontalPosition === 'number' ? style.horizontalPosition : .5;
        setText('stylePosition', v < .2 ? 'Near the top' : v < .4 ? 'Above the middle' : v < .6 ? 'Middle of the screen' : v < .8 ? 'Below the middle' : 'Near the bottom');
        setText('styleSide', Math.abs(h - .5) < .01 ? 'Centred' : h < .5 ? (h <= .3 ? 'Towards the left' : 'Left of centre') : (h >= .7 ? 'Towards the right' : 'Right of centre'));
        const sample = document.getElementById('styleSample');
        sample.style.fontSize = Math.max(16, Math.min(32, style.textSizeSp * .7)) + 'px';
        placeSample(v, h);
        const chosenColor = (style.palette || {})[style.color];
        const textColor = HEX_COLOR.test(chosenColor || '') ? chosenColor : '#FFE500';

        // Backgrounds: one tile each, showing sample text on it, built once with textContent/aria labels
        const backgrounds = Array.isArray(style.backgrounds) ? style.backgrounds : [];
        const current = backgrounds.find(b => b.name === style.background) || {};
        applyLook(document.getElementById('styleSampleText'), backgroundLook(current, textColor));
        setText('styleBackground', BACKGROUND_NAMES[style.background] || style.background || '');
        const tiles = document.getElementById('styleBackgrounds');
        if (tiles.childElementCount === 0) {
            for (const background of backgrounds) {
                const button = document.createElement('button');
                button.className = 'background-option';
                button.dataset.background = background.name;
                const label = BACKGROUND_NAMES[background.name] || background.name;
                button.setAttribute('aria-label', label);
                button.title = label;
                const text = document.createElement('span');
                text.textContent = 'Aa';
                text.setAttribute('aria-hidden', 'true');
                button.appendChild(text);
                button.addEventListener('click', () => setStyle('background=' + encodeURIComponent(background.name)));
                tiles.appendChild(button);
            }
        }
        for (const button of tiles.children) {
            const background = backgrounds.find(b => b.name === button.dataset.background) || {};
            applyLook(button.firstChild, backgroundLook(background, textColor));
            button.classList.toggle('active', button.dataset.background === style.background);
            button.setAttribute('aria-pressed', String(button.dataset.background === style.background));
        }

        // A light background brings its own (black) text colour, so the colour choice waits until it's dark again
        const textFixed = HEX_COLOR.test(current.text || '');
        setText('styleColor', textFixed ? 'Black, on a white background' : (style.color || '').replace(/^./, c => c.toUpperCase()));

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
            button.disabled = textFixed;
        }
    }

    async function togglePlay() {
        if (await command('/api/toggle-play')) await fetchStatus();
    }

    async function searchManual() {
        const input = document.getElementById('searchInput');
        const query = input.value.trim();
        if (!query) return;
        input.blur();
        openResults(query);
        if (await command('/api/search?q=' + encodeURIComponent(query))) {
            startResultsPolling();
            fetchStatus();
        } else {
            closeResults();
        }
    }

    async function uploadFile(input) {
        if (!input.files || !input.files.length) return;
        const file = input.files[0];
        const formData = new FormData();
        formData.append('subtitle', file);
        notify('Adding ' + file.name + '…');
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
    // The timing tip shows as a card on every visit; once dismissed, it's one tap away behind the heading's info button
    function dismissTimingTip() {
        document.getElementById('timingTip').hidden = true;
        const button = document.getElementById('timingInfoButton');
        button.hidden = false;
        button.focus();
    }

    function toggleTimingInfo() {
        const info = document.getElementById('timingInfo');
        info.hidden = !info.hidden;
        document.getElementById('timingInfoButton').setAttribute('aria-expanded', String(!info.hidden));
    }

    function closeTimingInfo() {
        if (document.getElementById('timingInfo').hidden) return;
        toggleTimingInfo();
        document.getElementById('timingInfoButton').focus();
    }

    // A tap anywhere else closes the tip
    document.addEventListener('click', event => {
        if (!event.target.closest('#timingInfo, #timingInfoButton')) {
            const info = document.getElementById('timingInfo');
            if (!info.hidden) { info.hidden = true; document.getElementById('timingInfoButton').setAttribute('aria-expanded', 'false'); }
        }
    });

    document.addEventListener('keydown', event => {
        if (event.key === 'Escape') closeTimingInfo();
        if (event.key === 'Escape' && sheetOpen) closeResults();
    });
    renderSteps();
    setInterval(fetchStatus, 2000);
    start();
</script>
</body>
</html>
""".trimIndent()
}
