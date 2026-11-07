# Build + Install UpMessenger via ADB (Mesin 4GB)

## 1. Syarat (sekali saja)
- Android Studio / SDK dengan `platform android-34`, `build-tools 34.0.0`.
- HP Android aktifkan Developer Options > USB Debugging, colok USB.
- Tutup Chrome/Emulator agar tidak freeze.

## 2. Build hemat RAM
```sh
cd mobile
export GRADLE_USER_HOME=/tmp/.gradle PATH=/opt/android-sdk/platform-tools:$PATH
./gradlew :app:assembleDebug --offline --max-workers=2
```
Artefak: `mobile/app/build/outputs/apk/debug/app-debug.apk`

## 3. Install via ADB ke HP fisik
```sh
export PATH=/opt/android-sdk/platform-tools:$PATH
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.upme.messenger/.presentation.MainActivity
adb logcat --pid=$(adb shell pidof com.upme.messenger)
```

## 4. Backend Elixir
```sh
cd backend
mix deps.get
mix test
UPME_PORT=5222 mix run --no-halt
```
Ganti `TcpClient(host)` dengan IP PC agar HP fisik konek (bukan 10.0.2.2).
