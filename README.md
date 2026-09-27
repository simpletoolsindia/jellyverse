# JellyVerse

_Your whole media universe_ – independent app, not affiliated with the Jellyfin project.

Native Android (Kotlin + Jetpack Compose) apps for a self-hosted homelab:

- **JellyVerse** (phone/tablet, `com.sridhar.jellyverse`) – Jellyfin, qBittorrent, aria2, Jellyseerr, Sonarr, Radarr, homelab health, SSH terminal and an on-device Qwen AI assistant.
- **JellyVerse TV** (Android TV, `com.sridhar.jellyverse.tv`) – a Hotstar-style, remote-first Jellyfin player.

Both are product flavors of one project (add `-Pstore` for Play builds without preloaded IPTV directories): `./gradlew assemblePhoneRelease assembleTvRelease` (APKs) or `bundlePhoneRelease bundleTvRelease` (Play AABs). Release artifacts land in `release/`; Play listing material and the publishing guide are in `play/`.

## Features

**Watch (Jellyfin)**
- Cinematic home: auto-advancing parallax hero, Continue watching, Next up, "New in …" rails per library
- Library grids with sort (added / name / release / rating / shuffle) and filters (unwatched / favorites), infinite scroll
- Detail pages: resume / start over, mark watched, favorite, seasons & episodes, cast, media info, similar titles
- Offline downloads (original file via Android DownloadManager) with an offline shelf that plays without the server

**Player (Media3 / ExoPlayer)**
- Double-tap ±10 s (accumulating), horizontal swipe to scrub, left/right vertical swipe for brightness/volume, pinch to zoom
- Skip Intro / Recap (Jellyfin media segments), Up Next card with autoplay, chapter ticks on the seek bar
- Audio & subtitle track picker, speed 0.5–2×, aspect Fit/Zoom/Stretch, sleep timer (15/30/60 min or end of episode)
- Quality: direct play or server transcode (20/8/4/2 Mbps HLS); **automatic fallback to transcoding** when the phone can't decode the original codec (e.g. DTS/TrueHD audio)
- Lock mode, picture-in-picture, progress synced back to Jellyfin

**Torrents (qBittorrent WebUI API v2)**
- Live speed dashboard with a 2-minute sparkline, session totals, connection status
- Filters (downloading / seeding / paused / completed / errored), search, sort
- Pause/resume, delete (optionally with files), recheck, reannounce, force start, sequential download, category, per-file selection
- Multi-select batch actions (long-press)
- Add by magnet / URL / .torrent file, with save path, category, start paused, sequential
- Global **download & upload limits**, alternative "turtle" limits + toggle, per-torrent limits
- Tapping a `magnet:` link or opening a `.torrent` file anywhere on the phone sends it to JellyVerse

**Manage (Sonarr + Radarr)**
- Sign in once with username/password – JellyVerse reads and stores only the API key
- Combined download queue (live), 30-day release calendar, wanted/missing with one-tap or "search all" automatic search
- **Manual search**: every indexer result with quality, size, seeders, age, custom-format score and rejection reasons – tap to grab
- Movie & series libraries with filters; detail pages with monitor toggles, per-season/per-episode search, delete (optionally with files)
- Disk space and health warnings from both apps

**Lab (homelab health over SSH – nothing installed on the server)**
- Health banner (CPU, memory, swap, temperature, full disks, unhealthy/crashed containers)
- Animated CPU / memory / temperature rings, live CPU+RAM history, network throughput, load average, uptime
- Storage bars per volume, swap meter
- Every Docker container with state, health and CPU; start / stop / restart and log viewer
- Top processes by CPU with SIGTERM

**Terminal (Termius-style SSH)**
- Real terminal emulator (xterm.js) – colours, htop, vim, less, resize with keyboard
- Two-row key bar: ESC, TAB, sticky CTRL/ALT (tap once / double-tap to lock), ↑↓←→ with hold-to-repeat, HOME/END/PGUP/PGDN, `/ - | ~`
- Quick chips: ^C ^D ^Z ^L ^R ^A ^E ^W, `:wq`; saved command snippets
- Multiple saved hosts, font size, reconnect, host-key pinning (warns if the server key changes), credentials in EncryptedSharedPreferences

**JellyVerse AI (on-device Qwen2.5-0.5B via MediaPipe)**
- 21 tools: search/play (fuzzy, "play X on TV" → Chromecast), smart search, request, downloads status, pause/resume, speed limits, turtle mode, add links, server health, restart containers, missing/upcoming media, Library Doctor…
- Keyword router handles obvious commands instantly; Qwen picks tools for free-form questions; answers are always the tool's real result
- **Library Doctor**: finds unmatched/messy titles (e.g. `www.1tamilmv.com_aranmanai_2026.mp4`), cleans names (rules + Qwen few-shot that learns from your corrections), searches Jellyfin's metadata providers, applies matches so posters download, and can reorganise files over SSH with a dry-run preview

**Live TV (IPTV)** – phone & TV
- Your own M3U/M3U8 (with optional provider login + User-Agent) or Xtream Codes; XMLTV now/next guide
- Preloaded free directories (sideload builds): iptv-org all-languages (12k+ channels, filter by language), iptv-org Tamil & India, Free-TV curated, prabhacap Tamil Local
- Language/category/country filters, favourites, search, "Check channels" hides dead streams
- Live player: LIVE badge, CH+/CH− (▲/▼ on the remote), channel list (◀), zap banner, per-channel headers, HLS auto-retry

**Music (Navidrome / Subsonic)** – phone & TV
- Spotify-style home: greeting, quick picks, "Made for you" mixes, albums, artists, playlists, pull to refresh
- Now Playing with album-colour glow, swipe-to-skip artwork, synced lyrics, queue, song radio, likes
- Background play with lock-screen & Android Auto controls, sleep timer with fade-out, equalizer & bass boost, data saver, 512 MB smart cache
- **Radio**: save any FM / internet stream (MP3/AAC, Icecast/Shoutcast, HLS, .pls/.m3u) – paste a link or share one to the app

**Parental control**
- 4-digit PIN (stored as a salted hash) – works with touch, D-pad and remote number keys
- 18+ titles (R, NC-17, TV-MA, A, 18+) stay off Home and live in a PIN-locked 🔞 menu, which can itself be hidden
- Lock any individual title; the PIN is asked before a single frame plays – online or offline

**Player extras**
- Quality chip (remembered), audio-language and CC quick chips, subtitle search & download (Jellyfin subtitle provider)
- Hold left/right to rewind/fast-forward (accelerates), ♥ favourite from the player, remote works everywhere on TV
- Playback recovery ladder: direct play → server remux → software decoder → full re-encode

**Jellyfin admin dashboard** – users & policies, live sessions, libraries, scheduled tasks, activity, devices, plugins, API keys

**Phone ↔ TV** – pair your phone with JellyVerse TV over Wi-Fi (4-digit code): full remote (D-pad, swipe pad, volume, channels) and a keyboard that types into TV fields as you type

**Trailers & polish** – Hotstar-style trailer previews on the spotlight (YouTube trailers, or a muted clip of the film), animated launch intro, in-app updates from GitHub Releases (verified same-signer APK)

**Everything else** – Tamil (தமிழ்) UI, library filters by language / year / genre, voice play ("Play … on JellyVerse"), offline mode with downloads shelf, tablet / foldable / landscape layouts, swimming-jellyfish loaders, crash guard

**v2.1 extras** – loading screen before playback, smart notifications (downloads / requests / homelab), Quick Settings turtle tile, app shortcuts, fingerprint lock, TV "Play Next" row on the Android TV home, TV genre/My List rows, smart OK (skip intro / next episode), low-RAM tuning

**Cast & Quick Connect**
- Chromecast from the player, a global mini cast bar, and via the AI
- Jellyfin Quick Connect sign-in (show code) and approval (enter a TV's code) on both apps

**Discover & Requests (Jellyseerr)**
- Trending spotlight, popular/upcoming movies & series, search, availability badges
- Request movies, or pick specific seasons for series; watch trailer; jump straight into Jellyfin when available
- Requests list with counters, filters, approve / decline / retry / remove (for managers)
- **User management**: permissions editor, delete users, import users from Jellyfin

## Build

Requires JDK 21 and the Android SDK (compile SDK 36).

```bash
./gradlew assemblePhoneRelease assembleTvRelease        # sideload APKs
./gradlew -Pstore bundlePhoneRelease bundleTvRelease    # Play Store AABs
./gradlew testPhoneDebugUnitTest                        # unit tests
```

Release signing reads `keystore.properties` (not committed); without it, release builds fall back to the debug key.

## Server setup notes

| Service      | Default in app               | Notes |
|--------------|------------------------------|-------|
| Jellyfin     | `http://<server-ip>:8096`   | Sign in with your Jellyfin user |
| qBittorrent  | `http://<server-ip>:8080`   | WebUI behind gluetun; WebUI username/password |
| Jellyseerr   | `https://requests.example.com` | Via Nginx Proxy Manager / Cloudflare. Jellyfin login or API key |
| Sonarr       | `https://sonarr.example.com`   | Username/password → API key |
| Radarr       | `https://radarr.example.com`   | Username/password → API key |
| SSH          | `<server-ip>:22`            | Used by Lab and Terminal (LAN only) |

Plain HTTP on the LAN is allowed (`network_security_config.xml`).
