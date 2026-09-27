# Play Console – Data safety answers (both apps)

- Does your app collect or share any of the required user data types? **No**
  (all data stays on the device or goes only to servers the user configures; the developer receives nothing)
- Is all user data encrypted in transit? **Depends on the user's server (HTTP on LAN is allowed)** → answer **No**, and explain LAN use
- Do you provide a way for users to request that their data is deleted? **Yes** – uninstall / "Sign out everywhere" deletes all local data
- Ads: **No** · In-app purchases: **No**
- Target audience: **18+** (self-hosted media management)
- App access for review: provide a demo Jellyfin server URL + test account in "App access", otherwise reviewers only see the setup screen
