# NBT-AAD-Helper

Android app that edits `/opt/aa/nbt-aad.env` on a BMW / Toyota Supra NBT Evo head unit running
[NBT-AAD](https://github.com/Sq00ky/Supra-iDrive6-Android-Auto) (native Android Auto).

Status: tested in a car for detection, reset, read and save. The installer ("Install / update NBT Android Auto") failed its first car test because the head unit's SSH login has no `chmod`, `wc` or `cksum`; version 1.0.13 is built for that and has not been tried in a car yet. EXPERIMENTAL and CONTRIBUTE are announced but not built.

## Get the APK
Every successful build is published as a release (Releases page, newest on top, `NBT-AAD-Helper-v1.0.N.apk`).
Download the APK on the phone, install it and allow "install unknown apps". The same file is also the artifact
`NbtAadEditor-debug-apk` of the "Build APK" run (Actions tab).

You can also open the folder in Android Studio (Ladybug or newer) and use Build > Build APK(s).

## Updates
At start-up the app asks GitHub for the latest release. When it is newer than the installed one, the first screen shows
"New version available", the release notes and an UPDATE button. UPDATE downloads the APK, checks its size, its
checksum and that it is signed with the same key as the installed app, and hands it to Android's installer, which asks
for one confirmation (Android does not allow an app to install itself silently). The app closes while it is replaced:
the banner says so, and after opening it again the first screen says "Updated to version X". The first time, Android also asks
to allow "install unknown apps" for this app.

How it fits together: the build number is the GitHub run number (version `1.0.N`, versionCode `N`), the workflow
signs every build with the fixed key in `signing/` and publishes release `v1.0.N`, and the app compares `N` with its own
versionCode. The updater needs internet, like the settings sync; with none it stays quiet.

Builds made before the updater (and signed with a random key) cannot be updated in place: uninstall the old app
once, then install a release. From then on updates install over the existing app and keep its data.

## Use
1. Open the app with internet (mobile data, or a WiFi repeater with internet). It checks the project's
   README for new settings and says "Available settings are synchronized"; the list is kept on the phone, and a
   copy is built into the app. Internet is also needed for the app's own updates and for the releases list and
   downloads of the installer.
2. Phone hotspot on, WiFi dongle connected to it (or Ethernet with OTG), car on (ignition / key on).
3. DETECT CAR. If the dot is red, use "Reset head unit" (no SSH needed) and wait for green.
4. NEXT opens the page where you choose what to do, four buttons: **CHANGE A SETTING**, **INSTALL / UPDATE NBT
   ANDROID AUTO**, **EXPERIMENTAL** (browse issues and pull requests, scan them for files; coming next) and
   **CONTRIBUTE** (pick log files from the head unit, zip them, open the issue page in the browser; coming later).

### Change a setting
Every documented setting is listed. READ OPTIONS marks the ones the head unit applies right now
(green, "Currently applied"). Change values with the dropdowns (Custom... for any other value), then SAVE.
A setting that the list does not have yet (from a newer or experimental build) is added with
"+ Add an option": a name and, if wanted, a value. It is kept on the phone under "Additional options" (first in the
list), whatever the settings sync brings, and "Remove from list" takes it out again.
Then restart the Android Auto Daemon from the head unit (Media/Radio > Android Auto > Force Stop).

### Install / update NBT Android Auto
Installs NBT-AAD files on the head unit from the phone, over the SSH login the app already has (the app does not
open SSH itself). It does by program what Sq00ky's `updating.md` does by hand with a USB stick:

- The source is a release from Sq00ky's Releases page (the app lists them, pre-releases marked), or any file or
  files on the phone: a zip, `nbt-aad`, `Releases.zip`, single plugin files. Zip files are opened, also a zip in a zip.
  The 7 names of a build are `nbt-aad`, `pf_aa` and the five plugin files; 80% of them (6 of 7, which is what a
  release has) make it "a build". A lone `nbt-aad` is a daemon test build. Every program is checked to be a
  32-bit ARM file, text files are checked to be text, and the version inside the daemon is read. Zip entries with odd
  paths are skipped and sizes are capped. Files from the phone get a clear warning and need an extra confirmation.
- INSTALL stops Android Auto (it asks you to Force Stop it, and also tries `kill`), makes the partition writable,
  uploads each file under a temporary name (`nbt-aad.new`, `NbtAad.new/…`) and checks it arrived intact (the head
  unit's `cksum`, else a read-back). Only then the old file is renamed to `nbt-aad.old` (the plugin folder to
  `/opt/aa/NbtAad.old`, never left inside `Plugins`), and the new one takes its place. Nothing is overwritten while it
  is in use. `nbt-aad.env` is not touched. `pf_aa` is not installed: the firewall rules are built into `nbt-aad`.
- Afterwards you restart the head unit (hold the power button 30 seconds), as the update guide says.
- "Go back to the previous version" swaps the daemon and/or the plugin with the `.old` ones (you tick which);
  pressing it again goes forward. To return to an official version, install it from the releases list.
- The SSH login of the head unit has only a few commands (`cat`, `cp`, `mv`, `rm`, `mkdir` and the shell itself).
  The scripts use only those: the new daemon is made by copying a program that is already executable and writing the
  file into the copy, so no `chmod` is needed, and the swap is refused if the new file is not executable. If an
  `nbt-aad` cannot be run (an install that stopped halfway), the card says so in red and "Go back" repairs it.
  "TOOLS CHECK" shows which commands the login has and can be copied to be sent along with a bug report.

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
