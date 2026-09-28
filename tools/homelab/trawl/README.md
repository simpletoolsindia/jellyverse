# TRAWL on the Raspberry Pi 5

[TRAWL](https://github.com/germondai/trawl) is a self-hosted anti-bot/challenge solver with a
FlareSolverr-compatible `/v1` endpoint, so Prowlarr can use it for Cloudflare-protected indexers.
The upstream image is multi-arch (`linux/arm64` included) – nothing to build on the Pi.

## Install

```bash
sudo mkdir -p /opt/homelab/compose/media/trawl && cd /opt/homelab/compose/media/trawl
sudo cp ~/Harbor/tools/homelab/trawl/compose.yml .           # or scp from the Mac
sudo cp ~/Harbor/tools/homelab/trawl/.env.example .env && sudo chmod 600 .env
sudo sed -i "s/^METRICS_DASHBOARD_TOKEN=.*/METRICS_DASHBOARD_TOKEN=$(openssl rand -hex 24)/" .env
sudo docker compose pull && sudo docker compose up -d
curl -s http://localhost:8191/health          # wait ~1–2 min on first boot
```

If FlareSolverr already listens on 8191, stop it first (`docker stop flaresolverr`) – TRAWL replaces it.

## Connect Prowlarr

1. Prowlarr → Settings → Indexers → **+** → **FlareSolverr**
2. Name `TRAWL`, Tags `flaresolverr`, Host `http://192.168.1.30:8191/` (or `http://trawl:8191/` if Prowlarr
   shares its Docker network), Request timeout `90`.
3. Add the `flaresolverr` tag to the Cloudflare-protected indexers → **Test**.

Sonarr / Radarr need nothing: they search through Prowlarr.

## Check it

```bash
curl -s -X POST http://localhost:8191/v1 -H 'Content-Type: application/json' \
  -d '{"cmd":"request.get","url":"https://nowsecure.nl","maxTimeout":90000}' | head -c 300
docker stats --no-stream trawl trawl-redis
```

Dashboard: `http://192.168.1.30:8191/dashboard` (token from `.env`).

## Pi notes

- Keep `BROWSER_POOL_SIZE=1`; raise it only on the 8 GB Pi and only if Prowlarr reports 429s.
- `mem_limit: 1536m` stops a runaway browser from pushing Jellyfin into swap.
- An NVMe/SSD for Docker's data root helps: Camoufox's first launch reads ~300 MB.
