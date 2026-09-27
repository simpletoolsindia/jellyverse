# Publishing JellyVerse & JellyVerse TV on Google Play

Two separate Play listings (different package names):

| App | Package | Upload |
|---|---|---|
| JellyVerse (phone/tablet) | `com.sridhar.jellyverse` | `release/JellyVerse-2.1.0-phone.aab` |
| JellyVerse TV (Android TV) | `com.sridhar.jellyverse.tv` | `release/JellyVerseTV-2.1.0-tv.aab` |

## One-time setup
1. Play Console → **Create app** (twice – one per package). Category: *Video Players & Editors*.
2. **App signing**: keep *Play App Signing* on. Upload key = `keystore/harbor-upload.jks` (alias `harbor-upload`, passwords in `keystore.properties`).
   **Back up both files somewhere safe** – losing the upload key means a key-reset request to Google.
3. **Store listing**: copy text from `play/<app>/en-US/*.txt`, images from `play/<app>/en-US/images/`
   (icon 512², feature graphic 1024×500, phone screenshots 1080×2160, TV banner 1280×720, TV screenshots 1920×1080).
4. **Privacy policy**: host `play/PRIVACY_POLICY.md` publicly (GitHub Pages, your site) and paste the URL.
5. **Data safety**: answers in `play/DATA_SAFETY.md`.
6. **App access**: reviewers need a server. Provide a demo Jellyfin URL + test user (e.g. a limited account exposed via
   `jellyfin.example.com`) – otherwise they only see the setup screen and may reject for "incomplete functionality".
7. **Content rating** questionnaire: no user-generated content, no ads → typically *Everyone/3+*… but torrent/download
   management usually lands at *Teen*; answer honestly.
8. **Android TV** (JellyVerse TV only): Advanced settings → Form factors → add *Android TV*, upload the TV banner and
   TV screenshots, and opt in to TV review.

## Every release
```bash
# bump versionCode / versionName in app/build.gradle.kts, then:
./gradlew bundlePhoneRelease bundleTvRelease -Pstore   # -Pstore = no preloaded IPTV lists (Play policy)
```
Upload the AABs to *Internal testing* first, install from the testing link, then promote to Production.

## Policy notes (read before submitting)
- **Screenshots** must not show copyrighted posters/stills you don't own – re-shoot against a demo library with
  public-domain films (e.g. Blender open movies) before publishing.
- Google reviews download/torrent managers strictly. Keep the listing focused on *managing your own server*; the
  qBittorrent/aria2 features are remote controls. If rejected, ship the phone app with those tabs hidden via a flavor.
- targetSdk 36, 16 KB page-size aligned native libraries (verified), no dangerous permissions.

- **IPTV**: Play only accepts "bring your own playlist" players. The `-Pstore` bundles ship with no channel lists;
  the sideload APKs in `release/` include the free iptv-org / Free-TV / prabhacap directories.
