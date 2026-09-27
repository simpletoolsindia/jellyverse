# JellyVerse – Privacy Policy

_Last updated: 26 September 2026_

JellyVerse and JellyVerse TV ("the apps") are clients for servers that **you** operate (Jellyfin, Jellyseerr, qBittorrent, aria2, Sonarr, Radarr and SSH hosts).

**What we collect:** nothing. The developer runs no servers and receives no data, analytics, crash reports or identifiers.

**Data stored on your device:** server addresses, usernames, access tokens/API keys and (optionally) passwords you enter, so the apps can reconnect. SSH credentials are stored with Android's EncryptedSharedPreferences. Offline downloads and the optional AI model are stored in the app's private storage. Uninstalling the app deletes all of it.

**Network traffic:** the apps connect only to the servers you configure, plus:
- TMDB image servers (image.tmdb.org) to show posters returned by Jellyseerr/Sonarr/Radarr
- Hugging Face (huggingface.co) – only if you tap "Download AI model", to fetch the Qwen model file
- Google Cast – only if you choose to cast to a Chromecast on your network

**On-device AI:** JellyVerse AI runs the Qwen model locally. Your prompts never leave the device.

**Voice input:** uses Android's speech recognizer only when you tap the microphone; audio is handled by your device's speech service under its own privacy policy.

**Children:** the apps are not directed at children.

**Contact:** sridharhomelab@gmail.com

**Navidrome (music):** JellyVerse stores only a salted authentication token for your Navidrome server – never your password. Played songs may be cached on the device (up to 512 MB) for smoother playback; clearing the app's cache removes them.

