# NBT-AAD-Helper

Android app that edits `/opt/aa/nbt-aad.env` on a BMW / Toyota Supra NBT Evo head unit running
[NBT-AAD](https://github.com/Sq00ky/Supra-iDrive6-Android-Auto) (native Android Auto).

Status: source only, not yet compiled or tested on a car.

## Get the APK
Every push runs the "Build APK" workflow (Actions tab). Open the finished run and download the artifact
`NbtAadEditor-debug-apk` (a zip containing `app-debug.apk`). Install it on the phone and allow
"install unknown apps".

You can also open the folder in Android Studio (Ladybug or newer) and use Build > Build APK(s).

## Use
1. Phone hotspot on, WiFi dongle connected to it, car on (ignition / key on).
2. DETECT CAR. If the dot is red, use "Reset head unit" (no SSH needed) and wait for green.
3. NEXT, READ OPTIONS, change values, SAVE.
4. Restart the Android Auto Daemon from the head unit (Media/Radio > Android Auto > Force Stop).

Files per car are kept on the phone in app-private storage under `files/cars/<VIN>/`.
