# DropBridge Android app (builds on GitHub, no Android Studio needed)

1. Create a new GitHub repository and upload the CONTENTS of this folder (app/, .github/, settings.gradle.kts ...).
   If the hidden .github folder doesn't upload: Add file > Create new file > `.github/workflows/build.yml`, paste the file from this zip.
2. Every push builds the APK automatically (Actions tab, about 5 min) and publishes it to Releases.
3. Releases > "DropBridge for Android (latest build)" > DropBridge.apk. Install it and allow "Install unknown apps".
   Builds are signed with the key in app/dropbridge.p12, so new versions install over old ones. No uninstalling.

Use: open the app > Scan QR code (from DropBridge on your PC). Wi-Fi/hotspot links and online sessions both work.
In any app: Share > DropBridge sends links, photos, videos and files to the PC.
Saving: tap Save on a file and it goes to your Downloads folder.
