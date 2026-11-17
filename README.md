# NBT-AAD-Helper

Android app that edits `/opt/aa/nbt-aad.env` on a BMW / Toyota Supra NBT Evo head unit running
[NBT-AAD](https://github.com/Sq00ky/Supra-iDrive6-Android-Auto) (native Android Auto).

Status: tested in a car for detection, reset, read and save. The settings list is new.

## Get the APK
Every push runs the "Build APK" workflow (Actions tab). Open the finished run and download the artifact
`NbtAadEditor-debug-apk` (a zip containing `app-debug.apk`). Install it on the phone and allow
"install unknown apps".

You can also open the folder in Android Studio (Ladybug or newer) and use Build > Build APK(s).

## Use
1. Open the app once with internet (mobile data, or a WiFi repeater with internet). It checks the project's
   README for new settings and says "Available settings are synchronized". After that no internet is needed;
   the list is kept on the phone, and a copy is built into the app.
2. Phone hotspot on, WiFi dongle connected to it (or Ethernet with OTG), car on (ignition / key on).
3. DETECT CAR. If the dot is red, use "Reset head unit" (no SSH needed) and wait for green.
4. NEXT. Every documented setting is listed. READ OPTIONS marks the ones the head unit applies right now
   (green, "Currently applied"). Change values with the dropdowns (Custom... for any other value), then SAVE.
5. Restart the Android Auto Daemon from the head unit (Media/Radio > Android Auto > Force Stop).

`nbt-aad.env` is optional and only overrides the daemon's defaults, so the app writes only the settings you
switch on. Limits of the loader on the head unit, checked before every upload: at most 15 settings, at most
159 characters per line.

## Where the settings list comes from
The README's "Configuration (`nbt-aad.env`)" section, parsed by `env/ConfigParser.kt` (the three tables, rows with
several settings, `KEY=a|b|c` forms, `NBT_KEY_<SLOT>`). `env/ConfigOverrides.kt` refines the settings whose
choices are only described in prose; a setting that is new in the README still appears with its README text.
The bundled copy is `app/src/main/assets/nbt-aad-config.md`; unit tests run against it in the build.

Files per car are kept on the phone in app-private storage under `files/cars/<VIN>/`. On the head unit the app keeps
`nbt-aad.env.orig` (the first version it saw) and `nbt-aad.env.bak`, `.bak2`, `.bak3` (the last three versions).
