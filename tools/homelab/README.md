# jv-import

Moves finished qBittorrent / aria2 downloads into the Jellyfin Movies / TV folders and triggers a library scan.
Install: copy `jv-import.py` to `/opt/homelab/scripts/`, the units to `/etc/systemd/system/`, create `/opt/homelab/secrets/jv-import.env` (root, 600) with `JELLYFIN_KEY=`, `QBIT_USER=`, `QBIT_PASS=`, then `systemctl enable --now jv-import.timer`. Log: `/var/log/jv-import.log`. Dry run: `python3 jv-import.py --dry-run`.
