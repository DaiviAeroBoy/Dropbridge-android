# DropBridge Android app (builds on GitHub, no Android Studio needed)

1. Create a new GitHub repository (github.com/new).
2. Upload the CONTENTS of this folder to it (settings.gradle.kts, app/, .github/ ...).
   - If the hidden .github folder doesn't upload: Add file > Create new file, type
     `.github/workflows/build.yml` as the name and paste in the build.yml from this zip.
3. Open the Actions tab. "Build APK" starts by itself (or click Run workflow). Takes about 5 minutes.
4. When it's green: Releases (right side of repo) > "DropBridge (latest build)" > download DropBridge.apk.
   Open it on your phone and allow "Install unknown apps".

Using it: open the app > Scan QR code (from DropBridge on your PC). In any app, tap Share > DropBridge
to send links, photos, videos and files straight to the PC. Works on Wi-Fi/hotspot without internet.
