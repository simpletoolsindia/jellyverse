#!/usr/bin/env python3
"""JellyVerse auto-import: finished downloads -> Movies / TV library, then a Jellyfin scan.

Runs every 2 min from a systemd timer. Handles:
  * qBittorrent torrents that are complete, not in an *arr category (Radarr/Sonarr import their own),
    and finished after this importer was installed (the existing backlog is never touched);
  * aria2 downloads that are complete (no .aria2 control file) and settled for 2 minutes.
Each video (>= 300 MB, not a sample) is moved to
  Movies: /mnt/pool/movies/Title (Year)/Title (Year).ext
  TV:     /mnt/data4tb/media/tv/Show/Season NN/Show SNNEMM.ext
with matching subtitles. Existing destinations are never overwritten. --dry-run only prints the plan.
"""
import http.cookiejar, json, os, re, shutil, sys, time, urllib.parse, urllib.request

MOVIES = "/mnt/pool/movies"
TV = "/mnt/data4tb/media/tv"
QB = "http://127.0.0.1:8080"
QB_HOST_ROOT = ("/downloads", "/mnt/data4tb/downloads")        # container path -> host path
QB_HOST_ROOT2 = ("/downloads/manual", "/mnt/data2tb/downloads/manual")
ARIA2_DIR = "/mnt/data4tb/downloads/aria2"
JF = "http://127.0.0.1:8096"
STATE = "/var/lib/jv-import/state.json"
ARR_CATEGORIES = {"movies-radarr", "tv-sonarr", "radarr", "sonarr", "prowlarr"}
VIDEO = {".mkv", ".mp4", ".avi", ".m4v", ".mov", ".ts", ".wmv", ".webm"}
SUBS = {".srt", ".ass", ".ssa", ".sub", ".vtt"}
MIN_SIZE = 300 * 1024 * 1024
DRY = "--dry-run" in sys.argv
ENV = dict(l.strip().split("=", 1) for l in open("/opt/homelab/secrets/jv-import.env") if "=" in l and not l.startswith("#"))


def log(msg):
    print(time.strftime("%Y-%m-%d %H:%M:%S"), ("[dry-run] " if DRY else "") + msg, flush=True)


def load_state():
    try:
        return json.load(open(STATE))
    except Exception:
        return {"installed": time.time(), "done": []}


def save_state(st):
    if DRY: return
    os.makedirs(os.path.dirname(STATE), exist_ok=True)
    json.dump(st, open(STATE + ".tmp", "w")); os.replace(STATE + ".tmp", STATE)


# ---------------- naming ----------------
JUNK = re.compile(r"\b(480p|576p|720p|1080p|2160p|4k|uhd|hdr|sdr|web-?dl|webrip|web|bluray|brrip|bdrip|hdrip|dvdrip|predvd|hdts|hdcam|camrip|"
                  r"x264|x265|h\.?264|h\.?265|hevc|avc|aac|dd\+?5\.1|ddp|atmos|esub|esubs|true|hq|clean|untouched|proper|repack|yts|rarbg)\b", re.I)


def clean(stem):
    s = re.sub(r"^\s*(www\.)?[\w-]+\.[a-z]{2,10}\s*[-–]\s*", "", stem, flags=re.I)   # "www.1TamilMV.ing - "
    s = re.sub(r"\[[^\]]*\]|\{[^}]*\}", " ", s)
    s = s.replace("_", " ")
    if s.count(".") >= 3 and " " not in s.strip(): s = s.replace(".", " ")
    tv = re.search(r"\bS(\d{1,2})\s*E(\d{1,3})\b", s, re.I)
    ym = re.search(r"[\(\[\s.](19\d\d|20\d\d)(?=[\)\]\s.]|$)", s)
    cut = min([m.start() for m in (tv, ym) if m] or [len(s)])
    title = JUNK.split(s[:cut])[0]
    title = re.sub(r"[\s\-–.()]+$", "", re.sub(r"\s+", " ", title)).strip(" -–.") or stem
    title = re.sub(r'[\\/:*?"<>|]', " ", title).strip()
    return title, (ym.group(1) if ym else None), (int(tv.group(1)), int(tv.group(2))) if tv else None


def target(path):
    stem, ext = os.path.splitext(os.path.basename(path))
    title, year, ep = clean(stem)
    if ep:
        show = title
        d = os.path.join(TV, show, "Season %02d" % ep[0])
        return d, "%s S%02dE%02d%s" % (show, ep[0], ep[1], ext.lower())
    name = f"{title} ({year})" if year else title
    return os.path.join(MOVIES, name), name + ext.lower()


# ---------------- moving ----------------
def videos_in(p):
    if os.path.isfile(p): return [p] if os.path.splitext(p)[1].lower() in VIDEO else []
    out = []
    for root, _, files in os.walk(p):
        for f in files:
            fp = os.path.join(root, f)
            if os.path.splitext(f)[1].lower() in VIDEO and "sample" not in f.lower() and os.path.getsize(fp) >= MIN_SIZE: out.append(fp)
    return out


def owner_of(d):
    st = os.stat(d); return st.st_uid, st.st_gid


def move(src):
    dst_dir, name = target(src)
    dst = os.path.join(dst_dir, name)
    if os.path.exists(dst):
        log(f"skip (already exists): {dst}"); return False
    log(f"move: {src}\n      -> {dst}")
    if DRY: return True
    os.makedirs(dst_dir, exist_ok=True)
    shutil.move(src, dst)
    # Subtitles next to the video ("Movie.en.srt" / "Movie.srt") travel with it.
    base = os.path.splitext(src)[0]
    for f in os.listdir(os.path.dirname(src)) if os.path.isdir(os.path.dirname(src)) else []:
        fp = os.path.join(os.path.dirname(src), f)
        if fp.startswith(base) and os.path.splitext(f)[1].lower() in SUBS:
            shutil.move(fp, os.path.join(dst_dir, os.path.splitext(name)[0] + fp[len(base):]))
    uid, gid = owner_of(MOVIES if dst_dir.startswith(MOVIES) else TV)
    for root, dirs, files in os.walk(dst_dir):
        for x in dirs + files:
            try: os.chown(os.path.join(root, x), uid, gid)
            except OSError: pass
    try: os.chown(dst_dir, uid, gid)
    except OSError: pass
    return True


def to_host(p):
    for c, h in (QB_HOST_ROOT2, QB_HOST_ROOT):
        if p == c or p.startswith(c + "/"): return h + p[len(c):]
    return p


# ---------------- sources ----------------
def qbit_opener():
    cj = http.cookiejar.CookieJar()
    op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))
    op.addheaders = [("Referer", QB)]
    op.open(QB + "/api/v2/auth/login", urllib.parse.urlencode({"username": ENV["QBIT_USER"], "password": ENV["QBIT_PASS"]}).encode(), timeout=15)
    return op


def from_qbit(st):
    moved = 0
    try:
        op = qbit_opener()
        torrents = json.load(op.open(QB + "/api/v2/torrents/info?filter=completed", timeout=15))
    except Exception as e:
        log(f"qBittorrent unavailable: {e}"); return 0
    for t in torrents:
        h = t["hash"]
        if h in st["done"] or t.get("category") in ARR_CATEGORIES or "jv-imported" in (t.get("tags") or ""): continue
        if t.get("completion_on", 0) < st["installed"] or t.get("progress", 0) < 1: continue
        files = videos_in(to_host(t["content_path"]))
        ok = [f for f in files if move(f)]
        moved += len(ok)
        if not DRY:
            st["done"].append(h)
            try: op.open(QB + "/api/v2/torrents/addTags", urllib.parse.urlencode({"hashes": h, "tags": "jv-imported"}).encode(), timeout=15)
            except Exception: pass
    return moved


def from_aria2(st):
    moved = 0
    if not os.path.isdir(ARIA2_DIR): return 0
    for root, _, files in os.walk(ARIA2_DIR):
        for f in files:
            fp = os.path.join(root, f)
            if os.path.splitext(f)[1].lower() not in VIDEO or os.path.exists(fp + ".aria2"): continue
            m = os.path.getmtime(fp)
            if m < st["installed"] or time.time() - m < 120 or os.path.getsize(fp) < MIN_SIZE or fp in st["done"]: continue
            if move(fp): moved += 1
            if not DRY: st["done"].append(fp)
    return moved


def jellyfin_scan():
    if DRY: log("would trigger a Jellyfin library scan"); return
    req = urllib.request.Request(JF + "/Library/Refresh", method="POST",
                                 headers={"Authorization": 'MediaBrowser Token="%s"' % ENV["JELLYFIN_KEY"]})
    try:
        urllib.request.urlopen(req, timeout=20); log("Jellyfin library scan started")
    except Exception as e:
        log(f"Jellyfin scan failed: {e}")


def main():
    st = load_state()
    if not os.path.exists(STATE): save_state(st)   # first run: remember the install time, touch nothing older
    n = from_qbit(st) + from_aria2(st)
    st["done"] = st["done"][-2000:]
    save_state(st)
    if n: jellyfin_scan()


if __name__ == "__main__":
    if "--name" in sys.argv:   # naming self-check: jv-import --name "<file name>"
        print(target(sys.argv[-1])); sys.exit(0)
    main()
