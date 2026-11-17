## Configuration (`nbt-aad.env`)

`/opt/aa/nbt-aad.env` holds one `NAME=value` per line. Lines starting with `#` and blank lines are skipped.

Limits and behaviour:
- **Only the first 15 settings are used, and each line is limited to 159 characters.**
- The file is read when the iDrive menu starts the daemon. A daemon started from a shell doesn't read it; put the
  settings in front of the command instead, e.g. `NBT_LOG_FILE=/mnt/umass01200t7/a.log /opt/aa/nbt-aad`.
- Changes take effect at the next **Start** (or Restart). No reboot is needed.

On/off settings accept `1`/`0`; most also accept `on`/`off`, `yes`/`no` and `true`/`false`.

### Common settings

| Setting | Default | Effect |
|---|---|---|
| `NBT_LOG_FILE` | (none) | Append the daemon log to this file; it survives a head-unit reset. Same name = appended |
| `NBT_MENU_RETURN` | home | Where iDrive goes once connected: `media` = Media/Radio list, `off` = stay in our menu |
| `NBT_RX_TIMEOUT_S` | 5 | Seconds without data from the phone before iDrive gets the screen back; `0` = off (2 before v1.9.32) |
| `NBT_VIDEO_QUEUE` | 15 | Maximum queued video frames (`1`-`60`). Lower it to reduce memory use and backlog on slower head units |
| `NBT_VID_FPS` | (unset) | `30` or `60`: the frame rate asked of the phone and the decoder's rate. Unset: the phone is asked for 30 fps (what it delivers over Wi-Fi anyway) and the decoder is declared at 60, as before. `30` declares the decoder at 30 too (smoother on a slower head unit, car-verified on an F22); `60` asks the phone for 60 |
| `NBT_BACK_HOLD_MS` | 2500 | BACK hold time to minimize; `0` = off (BACK forwarded at once) |
| `NBT_KNOB_INVERT` | off | `1` reverses the rotary direction |
| `NBT_KEY_<SLOT>` | see above | Android keycode for a hard key, `0` = ignore. SLOT = `MENU BACK OPTIONS COM MEDIA NAV MAP`. Defaults 3 HOME, 4 BACK, 0, 5 CALL, 65537 MEDIA, 65538 NAV, 65538 NAV |
| `NBT_KNOB_INST` | 03 | CCS35up controller instance (hex) |
| `NBT_KNOB_CCS_INST` | 01 | Older CCS controller instance (hex) |
| `NBT_VEHICLE` | from `carplay.ini` (CarPlay logo, then Manufacturer) | `toyota`, `bmw`, `mini` or `rollsroyce`: the car make Android Auto shows (exit icon) |
| `NBT_SCREEN` | from `carplay.ini` (a `1180_480` coding counts as a 1280x480 panel); without one, the display size LayerManager reports (v1.9.54), else 1280x480 | Panel size, e.g. `1440x540` (BMW 10.25") |

### Features you can turn off (all on by default)

| Setting | Turn off with | What it does |
|---|---|---|
| `NBT_WIRELESS` | `0` | HU-hosted wireless. Off = old mode where the daemon scans for a phone running "Start head unit server" |
| `NBT_AUDIO_BT` | `0` | Only read when `NBT_AUDIO_MODE` is unset. Media over Bluetooth A2DP (the default again since v1.8.66); `0` = experimental A2I pipeline (see below). Browser audio: `NBT_AUDIO_MODE=browser` |
| `NBT_DROP_IDR` | `0` | v1.9.53: after a dropped video frame the daemon asks the phone for a keyframe (video focus off, on again 500 ms later, at most once in 10 s). `0` = never ask: the picture then stays damaged until the phone's next keyframe |
| `NBT_GUIDANCE_BT` | `0` | Bluetooth mode only: nav prompts mixed into the Bluetooth audio (sink declared as system audio) |
| `NBT_VOICE_SCENARIO` | `0` | Car mic routed to the Assistant via the `Carplay_Siri_1mic` speech scenario. Without it the Assistant hears silence |
| `NBT_BT_REFOCUS` | `0` | Request the car's Bluetooth source again after the Assistant closes the mic, so the reply is heard |
| `NBT_MIC` | `0` | Car microphone (also `/opt/aa/mic.disable`) |
| `NBT_KNOB` | `0` | iDrive controller to the phone |
| `NBT_CAR_GPS` | `0` | The car's GPS position to the phone (LOCATION sensor). See also `NBT_PHONE_GPS` / **Use Phone GPS** |
| `NBT_AA_CAMERA_CTX` | `692,1023` | iDrive contexts (`hmi.graphicalContext` visibleContext, logged in `nbt-aad-app.log`) that are camera views: Android Auto steps aside while one is up and comes back when iDrive leaves it, whatever the checkboxes. For the button-opened 360 / Top View, which neither reverse nor park assist reports. 1023 = an F-series NBTevo_Y24073J, 692 = the Supra's Top View. A widgetless untitled screen over Android Auto that is not listed is logged as `camera: context N ... add it to NBT_AA_CAMERA_CTX` |
| `NBT_LCI_MEDIA` | `0` | Only matters when set: `1` = **Media Controls for LCI Steering Wheels** on, `0` = off, whatever the Settings checkbox says. On = the steering wheel's previous / next (`NBT_MFL_*`) become Android media keys in a non-Bluetooth audio mode. Off by default |
| `NBT_PHONE_GPS` | `0` | Only matters when set: `1` = Use Phone GPS on, `0` = off, whatever the Settings checkbox says. On = no car GPS read, and no location / compass / GPS satellite / accelerometer sensors offered |
| `NBT_CAR_SPEED` | `0` | The car's wheel speed to the phone (CAR_SPEED sensor) |
| `NBT_CAR_GEAR` | `0` | The car's gear to the phone (GEAR sensor). Reverse-gear handling is `NBT_GEAR_ACTION` |
| `NBT_CAR_PARKING_BRAKE` | `0` | The car's parking brake to the phone (PARKING_BRAKE sensor) |
| `NBT_TOUCH_FWD` | `0` | Touch to the phone |
| `NBT_HIDE_IDLE` | `0` | iDrive gets the screen while no phone is connected. Off = overlay stays up for the whole run |
| `NBT_GEAR_ACTION` | `0` | Reverse-gear handling |
| `NBT_CTL` | `0` | Single-instance control port (UDP 127.0.0.1:52790) |
| `NBT_KEEP_PF` | set to `1` | Don't run `pfctl -d` at start. Only with the Android Auto rules in `pf.conf`: `pf_aa --install` adds both (see [Troubleshooting](#troubleshooting)) |

### Advanced / experimental

Leave these unset unless you are testing.

| Setting | Effect |
|---|---|
| `NBT_AA_PORT` | TCP port the phone is told to use (default 5277). Port 52770 has been tried and failed on one car; under investigation |
| `NBT_AA_SURFACE` | LayerManager surface for the Android Auto picture (default `1282`, its own; `1280` = shared with the factory CarPlay as before v1.9.20) |
| `NBT_USB` | **Experiment, off.** Also the Settings checkbox **Android Auto over USB (beta)** (`/var/opt/aa/usb-aa`; a set `NBT_USB` wins either way, `0` forces it off; read at daemon start, so it applies at the next Start / Restart). `1` = Android Auto over USB through the head unit's own Android Open Accessory drivers, next to wireless. Not car-verified. With it on, an Android phone plugged in while the daemon runs is switched to accessory mode (`aoap-switch`, `/dev/android<bus><dev>`) as `Android` / `Android Auto`, and the session runs through io-usb (`NBT_USB_DIRECT`, on by default), or over `/dev/oap<bus><dev>` (`devc-serusb-aoap`) with `NBT_USB_DIRECT=0`. The phone then stays in accessory mode, so iDrive's USB media (MTP) doesn't see it until it is replugged. When the daemon stops it asks the phone to end a USB session (ByeByeRequest, waits up to 500 ms for the answer). After 3 sessions on one plug-in, replug it (a node that can't be opened or switched yet is retried every second and doesn't count as a session). USB and wireless share the one session: while a phone is on the cable the wireless thread leaves the Bluetooth links alone (`[Wireless] phone on the USB cable ...`, the same idle state as a car without a hotspot config); once USB gives up on the phone (out of tries or sessions, or switched by another client such as BMW's A4A) wireless is available again without unplugging it (`[Wireless] no phone for USB any more ...`) and a wireless connection that arrives anyway is refused (`USB Android Auto session running: refusing the wireless connection`); a phone plugged in during a wireless session waits until that session ends. Log: `[UsbAccessory] ...`. If the phone never answers, `NBT_USB_MANUFACTURER` / `NBT_USB_MODEL` change the identity sent |
| `NBT_USB_DIRECT` | **On** whenever Android Auto over USB is on (the checkbox or `NBT_USB=1`); `0` / `off` / `no` / `false` = read the phone through `/dev/oap*` instead of libusbdi (io-usb). `devc-serusb-aoap` lets any client read the node (the media app's A4A handler opens it too) and a transfer that arrives while our read is not waiting can go to the other reader or be lost in the driver's queue: TLS records went missing in bursts and the phone dropped the session (car logs 2026-10-31). With it on, once the phone is in accessory mode the daemon stops `devc-serusb-aoap` (`[UsbDirect] stopped devc-serusb-aoap`), attaches to the accessory itself and keeps 8 reads queued. It also switches phones itself: the head unit asks a new USB device whether it speaks Android accessory only once, at plug-in, so a phone still set to "charging only" got no `/dev/android*` node and nothing happened until a replug. A device that has no node half a second after it appears is asked again through io-usb every second for up to 2 minutes (`[UsbDirect] usb0.N.0: AOA protocol 2: switch to accessory mode sent`); a device that leaves the request unanswered for a second is not a phone and is not asked again, and the head unit's own Microchip/SMSC devices (vendor 0424) are never asked. And it keeps `devc-serusb-aoap` from starting in the first place: it claims `/opt/mm/bin/devc-serusb-aoap` in the QNX path space and refuses every open of it, so the head unit's start of the driver fails (it logs that and leaves the phone alone, no retry) and the phone is opened as soon as it appears (`[UsbDirect] claimed /opt/mm/bin/devc-serusb-aoap`). QNX drops the claim when nbt-aad exits; `NBT_USB_CLAIM_DRIVER=0` goes back to stopping the driver after it has started. It also resets a phone that can't start a session (`[UsbAccessory] ...: resetting it`): one left in accessory mode keeps streaming its earlier session and ignores a new one, which used to need a replug. An accessory already plugged in when the daemon starts is reset before its first session, a session whose first frames can't be decrypted ends after 16 of them and resets the phone, and a phone that stays silent gets one more try and then a reset. At most 2 resets, then 2 minutes without one. Falls back to `/dev/oap*` if io-usb can't be reached; a phone can't be reset through that. Log: `[UsbDirect] ...` |
| `NBT_BTSPP_DEV` | Use only this SPP node (default: scan `/dev/btspp0..7`) |
| `NBT_WPP_MODE=0\|1\|2` | Pin the Bluetooth handshake mode (default rotates 2, 0, 1 on failures) |
| `NBT_AP_CFG`, `NBT_AP_IF`, `NBT_AP_IP`, `NBT_AP_BSSID` | Hotspot config path / interface / address / BSSID (default: `iap2.cfg`, `iap2_Wifi.cfg` as fallback; `uap0`, 172.16.224.1) |
| `NBT_ALT_WIFI=DIRECT\|CARPLAY`, `NBT_WFD_IF`, `NBT_WFD_SSID` | **Default `CARPLAY`**; also the Settings checkbox **Use WiFi Direct Network** (= `DIRECT`; a set `NBT_ALT_WIFI` wins). `BOTH` was removed in v1.9.37 and now means `CARPLAY`. Which Wi-Fi the phone is offered, one per run with no fallback. `CARPLAY` (unset) = the CarPlay hotspot. `DIRECT` = the car's Wi-Fi Direct group only, no fallback (for cars without the CarPlay licence, where `iap2.cfg` is missing but `wfd0` runs because Miracast is coded): passphrase decoded from `/var/opt/conn/WFDPass.conf` (hex, XOR `NBTEvo0715`), SSID from `NBT_WFD_SSID`, `ifconfig <if>`, `mwud` 127.0.0.1:9096, or derived as `DIRECT-03-` + the WLAN device name from the WLAN persistency (the J29: `DIRECT-03-Toyota51807`). `NBT_WFD_IF` (default `wfd0`) and `NBT_WFD_SSID` pin the interface and the group name. The passphrase is logged once per value so it can be typed into a phone by hand. Not car-verified yet |
| `NBT_SPP_UUID_CON` | Connect UUID registered with the car's SPP service (default = AA UUID) |
| `NBT_SPP_REREG_S` | While no Android Auto Bluetooth link is open, register the service again every this many seconds for 2 minutes, then every 30 s (default 5; `0` = off) |
| `NBT_BT_TRACE` | `1` = read-only Bluetooth diagnostics: log, with uptime, when the phone connects to / drops from the car over Bluetooth and which services it has (`DeviceListChanged: phone ... is connected`), to see why the phone does not open the Android Auto link after the car sleeps and wakes. Off by default, not car-verified |
| `NBT_VOICE_SCENARIO_NAME` | Speech scenario name, e.g. `Carplay_Siri_1mic_RefCh` |
| `NBT_BT_SRC`, `NBT_BT_SINK`, `NBT_BT_RELEASE=1` | Audio source/sink for Bluetooth mode (default `0x3a` / `0x1`); release the source when the phone leaves |
| `NBT_AUDIOCONN`, `NBT_AUDIOCONN_SUFFIX`, `NBT_AUDIOCONN_RETRIES` | `audioconn` path (default `/fs/sda0/opt/mm/bin/audioconn`), extra args, retries while the car refuses the source (default 6, max 20), pauses `NBT_AUDIOCONN_RETRY_S` (default 5) s growing by 2 s up to 12 s |
| `NBT_BT_ROUTE_DELAY_MS` | wait before the Bluetooth-source `audioconn` after a session starts (default 1500 ms, 0 = at once). Since v1.8.41 the request is single-flight, at most one `audioconn` runs at a time, and a session that ends cancels its pending request and retries (a phone reconnecting every few seconds used to stack `audioconn` processes until the head unit ran out of memory and rebooted) |
| `NBT_CHILD_NO_PRELOAD` | **On by default since v1.9.69.** `LD_PRELOAD` is removed for every child process the daemon starts (`audioconn`, `LayerManagerControl`, `pfctl`, `arp`, `ifconfig`), so they stop loading the graphics libraries they never use (`ldd:FATAL: Could not preload library libEGL_X.gpp.so`, `unknown symbol: kdMain`). `NBT_CHILD_NO_PRELOAD=0` keeps it, which is only useful when debugging a child. The start-up line `[Main] LD_PRELOAD=...` says which way it went |
| `NBT_TOUCH_CONNECT`, `NBT_TOUCH_PEER` | On by default since v1.8.47 (`NBT_TOUCH_CONNECT=0` turns it off; car-verified on a G11: gestures work under Android Auto): while Android Auto is on screen the touch socket is connected to the touch controller (the first touch PDU's sender, or `NBT_TOUCH_PEER=ip:port`), so BMW Gesture Control PDUs on the same UDP 30567 port go to iDrive instead of being swallowed: circle clockwise / anticlockwise = volume up / down, two fingers = next track or mute (as set in iDrive). Swipe right and point only close / confirm iDrive popups, so over Android Auto they normally do nothing (iDrive does not even enable them in the camera unless a popup is up); swipe left is ignored by iDrive. The learned address is kept in `/var/opt/aa/touch-peer`, so after the first touch every later start connects at once. Car-confirmed (G11, v1.8.43): the camera sends from `160.48.199.16:30501`, ~30 PDUs/s of `0xB5308006` hand coordinates plus `0xB5308005` gestures. Gestures that still reach the daemon are logged decoded (`[TouchFwd] gesture camera: rotate clockwise (volume up) (id 3)`). If Android Auto touch stops working, set `NBT_TOUCH_CONNECT=0` and delete `/var/opt/aa/touch-peer` |
| `NBT_BT_PLAY` | **Experiment, off.** `1` = once the car grants the Bluetooth source, wait `NBT_BT_PLAY_DELAY` ms (1500) and send SourceActivity PLAY to it (`AuxInBTAudio.DMMAudioSource` request 2), like picking Bluetooth audio in iDrive; `2` = also AVRCP START (`BlueMedia.SGMedia`). Log: `[BtPlay] ...`. Not car-verified |
| `NBT_BT_CONN` | ConnId of the car's Bluetooth source, requested through the ConnectionMaster (`audioconn -b <id>`, the iDrive HMI's call). Default `0xc7` (`audioconn -l`: `eSRC_A2DP ... AuxInBTAudio srcNr: 0x1`), car-verified on a MINI F60 since v1.8.38; `0` = the old focus form `-b 0x3a 0x1` (`NBT_BT_SRC` / `NBT_BT_SINK`) |
| `NBT_SDR_DROP` | AA channels left out of service discovery (default `9` in Bluetooth mode) |
| `NBT_RAM_LOG` | `1` | v1.8.67: with no USB log, the daemon and the iDrive app log to RAM (`/dev/shmem/nbt-aad.log`, `/dev/shmem/nbt-aad-app.log`), fresh per run, capped at 5 MB each; at the cap RAM logging stops for that run. `0` = off |
| `NBT_AUDIO_MODE` | `bt` (default again since v1.8.66), `browser` (car-verified; the default in v1.8.61..v1.8.65) or `a2i`. `browser`: Android Auto sound comes over the Android Auto connection instead of Bluetooth and plays through the car's browser audio source (see [Browser audio mode](#browser-audio-mode)). `bt`: media, prompts and Assistant stay on Bluetooth A2DP. Unset follows `NBT_AUDIO_BT` if that is set (`1` = `bt`, `0` = `a2i`) |
| `NBT_BROWSER_CONN`, `NBT_BROWSER_IDLE_S`, `NBT_BROWSER_PROMPT_IDLE_MS`, `NBT_BROWSER_REFOCUS`, `NBT_BROWSER_PCM`, `NBT_BROWSER_RATE`, `NBT_BROWSER_FRAG`, `NBT_BROWSER_FRAGS`, `NBT_BROWSER_ASOUND` | Browser mode: connection id (default `0xb8`, `0` = don't route), release the source after N s without sound once AA media has played (default `10`, `0` = keep it until the session ends), release it N ms after a prompt or system sound when no AA media is playing (default `1000`, `0` = use the 10 s rule), ask for it again when the mic closes (default on), `card:device` instead of the preferred device, device rate (default `48000`; this car's browser device takes 22050..44100, so 44100 is used), fragment size in bytes (default `4096`, rounded down to a power of two: A2I refuses any other size), fragment count (default `5`; if the device refuses, the daemon tries twice the count, then 8 fragments of twice the size), shim path (default `/opt/mm/lib/libasound/libasound.so`) |
| `NBT_DUCK_DB`, `NBT_DUCK_ATTACK_MS`, `NBT_DUCK_HOLD_MS`, `NBT_DUCK_RELEASE_MS`, `NBT_MEDIA_GAIN_DB`, `NBT_GUIDANCE_GAIN_DB`, `NBT_SYSTEM_GAIN_DB` | Browser mode ducking: media level under a prompt or system sound (default `-12` dB), ramp down / hold / ramp up (default `80` / `600` / `500` ms), fixed per-stream gains (default `0` dB) |
| `NBT_SYSTEM_SINK` | `1` / `0` forces the SYSTEM_AUDIO sink (ch 16) in / out of service discovery (default: in browser mode only) |
| `NBT_BROWSER_WAIT_MS` | Browser mode: wait up to this long for a block of a playing stream that arrives a little late instead of padding it with silence (default `30`, max `60`, `0` = pad at once). Only when the device holds enough to cover the wait: the last write blocked, or the mixer is at least the wait + a block + 20 ms ahead of real time (on the car the shim never blocks and the mixer runs ~150 ms ahead). The 10 s stats show `[Browser] wait room: writes that blocked .. of .., lead over real time ..-.. ms`; real gaps fade instead of clicking |
| `NBT_BROWSER_PREROLL_MS` | Browser mode: hold each AA stream at its start until this much audio is queued, or its first audio has waited this long (default `60`, max `200`, `0` = play at once). The phone sends in real time with no burst at the start, so without it a stream has no cushion and every frame that arrives a little late becomes a ~4 ms hole (`ran dry: a block got 857 of 1024 frames`), heard as crackle in prompts and Assistant replies. A stream that runs empty while playing is held again. Adds about one frame period (~43 ms) to the start of a prompt. The 10 s stats show `[Browser] pre-roll holds media/guidance/system: ..`, the session summary `[Feature] preroll=..` |
| `NBT_BROWSER_DRIFT` | Browser mode: `0` turns off clock-drift correction for AA media. On by default: for the first 10 s of a stream it learns how much audio the phone keeps queued, then trims the media resampler by up to ±500 ppm (under 1 cent of pitch) to hold that level, so a phone whose clock runs slow doesn't drain it and crackle after half an hour, and a fast one doesn't make the delay creep up. The 10 s stats show `[Browser] media drift (PI): trim .. ppm, queue .. ms, target .. ms, off by .. ms, phone clock .. ppm` |
| `NBT_BROWSER_DRIFT_LOOP` | Browser mode: which loop sets the drift trim. Default `pi`: proportional-integral, holds the queue on its target (within ~1 ms in the one-hour simulation) and its integral settles on the phone's clock error, logged as `phone clock .. ppm` and in the session summary `[Feature] drift_loop=pi .. phone_clock=..`. `p` = the proportional trim alone, which sits ~0.1 ms off target per ppm of drift (10 ms at 100 ppm, 30 ms at 300 ppm) and gives no clock estimate |
| `NBT_AA_DEFER_ACKS`, `NBT_AA_MAX_UNACKED`, `NBT_AA_ACK_FORCE_MS` | Browser mode: `0` acks AA audio frames on arrival again. On by default: a frame is acked once the mixer has taken it, as Google's head unit does, so the phone can run at most `NBT_AA_MAX_UNACKED` frames ahead (default `10`, `4`..`64`, audio sinks only; below 4 every frame would end in a gap) and is paced by the car's clock. A frame unacked for `NBT_AA_ACK_FORCE_MS` (default `1000`, `0` = never) is acked anyway, so no device or a late grant slows the phone but never stalls it. Log: `[AASession] deferred audio acks, last 30 s: ..` and the session summary `[Feature] deferred_acks=on .. window_full=..` (how often the phone was held back). A phone held to the window keeps about window x frame length of media queued (~427 ms for 10 frames), so the media drift loop's target cap is raised to cover it (`[Browser] media drift: target cap raised to .. ms`); without that the loop would pin its trim at +500 ppm |
| `NBT_BROWSER_PRIO` | Browser mode: priority of the mixer thread, default `15` (above the default 10 the HMI and video threads run at, below the USB reader's 21), `0` = leave it alone. The mixer must write every ~23 ms, and with deferred acks it also paces the phone. The 10 s stats show `[Browser] mixer timing: block 23 ms, longest gap between writes .. ms, late gaps (> 46 ms) .., session .., thread priority ..`: late gaps eat the device's ~92 ms of headroom. The car's own audio threads (`pidin` on `io-audio`, `dev-libasound`) should stay above this value |
| `NBT_AA_MINOR` | Experiment: the Android Auto protocol minor version the daemon asks for, default `6` (1.6). Google's own head unit asks for `7`; the phone has answered 1.7 with status 0 in every car log either way. Only the number changes. Log: `[AASession] >>> VersionResponse: Major=1, Minor=7, Status=0 (SUCCESS; we asked for 1.6)` (a non-zero status now shows as `REFUSED` with a warning) and `[Feature] aa_version: asked 1.6, phone 1.7, status 0` |
| `NBT_AA_ACK_TIMES` | `0` leaves the arrival time out of AA audio acks. On by default: each audio ack carries the time its frame arrived (wall clock, ns), as Google's head unit sends it (Ack field 3); the phone may use it to measure latency. Video acks carry none, as on Google's head unit. Any mode |
| `NBT_GUIDANCE_RATE` | Browser mode: the only rate the guidance sink (ch 8) offers, default `48000`. The template offers 16 and 48 kHz and the phone picks one without asking (it took 48 kHz on the car), so a single offer leaves no doubt. `16000` offers only that; `0` keeps both offers. Log: `[SDR] channel 8 guidance sink: offering 48000 Hz only ..` |
| `NBT_AA_UNDERFLOW_REPORT` | Browser mode: `0` stops telling the phone when a stream ran dry. On by default: when a playing stream that has already sent audio leaves a mixer block short (padded with silence), the phone gets an AudioUnderflowNotification (`0x800b`), once per dry spell, as Google's head unit does. What the phone does with it isn't known yet. Log: `[Browser] media ran dry ..`, `[AASession] media ran dry: AudioUnderflowNotification (0x800b) #N ..`, 5 s later `[Browser] 5 s after media underflow #N: the phone sent X ms of audio in 5000 ms, queue now Y ms` (X above 5000 = it sent extra), and the session summary `[Feature] underflow_reports=on ..` |
| `NBT_BROWSER_RESAMPLER`, `NBT_BROWSER_SPEEX_Q`, `NBT_BROWSER_RESAMPLER_FILE` | Browser mode: how the AA streams are converted to the device rate. Default `speex`: the speexdsp resampler (see [Third-party code](#third-party-code)), quality `5` (0..10; 80-tap filter, ~100 dB stopband), flat to ~19.5 kHz with no aliasing and no treble wobble under the drift trim. `linear` = the old linear interpolation (about -2.9 dB at 15 kHz, 22-24 kHz folding back as an audible alias). To compare by ear while music plays: `echo linear > /tmp/nbt_resampler` (or `speex`), `rm /tmp/nbt_resampler` to go back; the file is read once a second (`NBT_BROWSER_RESAMPLER_FILE` moves it, empty = off). The 10 s stats show `[Browser] resampler speexdsp: X ms CPU per s (Y %), output/input vs rates media/guidance/system ..`; speex that takes over 25 % CPU or produces the wrong number of frames (over 2 % off) falls back to linear for the session, with a warning |
| `NBT_BROWSER_FORCE_RATE` | Browser mode experiment, off by default: ask the browser device for this rate (e.g. `48000`) even though it reports 22050..44100, so the 48 kHz AA streams play without resampling. The log says whether the device took it; if the car refuses, the device doesn't open and there is no AA audio that session (reason in `sloginfo`) |
| `NBT_SPEECH` | Play AA guidance (ch 8) on an A2I endpoint. In browser mode each prompt gets its own audio focus on the car's nav prompter, so the radio is ducked, not muted. While AA media plays, prompts stay in the browser mix (media ducked). On by default in browser mode (`0` = off, guidance in the browser mix), off in Bluetooth mode (`1` = on). See [Navigation prompts over the radio](#navigation-prompts-over-the-radio) |
| `NBT_SPEECH_EP` | Endpoint for the prompts. Default in browser mode `/input_mono_0`, the car's nav prompter; in Bluetooth mode `/input_stereo_AirPlayAlt`. `/input_mono_*` endpoints are opened mono, all others stereo |
| `NBT_SPEECH_SRC`, `NBT_SPEECH_SINK` | Focus requested for each prompt, `audioconn -b <src> <sink>`. `SRC` default in browser mode `0x1a` (`eSRC_NAV_AUTO`), in Bluetooth mode `0`. `0` = no focus request (Bluetooth mode: audio plays at once; browser mode: guidance stays in the browser mix). `SINK` default `0x1` (speakers) |
| `NBT_SPEECH_BT` | `1` = the **Prompts on car radio (BT, beta)** checkbox (`/var/opt/aa/speech-bt`; a set `NBT_SPEECH_BT` wins either way). Bluetooth audio mode only: turns the speech path on with the browser defaults (`/input_mono_0`, `0x1a`, sink `0x1`) and keeps channel 8 as GUIDANCE. Unset = off, the BT default (prompts over A2DP as before). |
| `NBT_SPEECH_HOLD_MS` | The focus is released this long after the queued audio has played, then the endpoint is closed (default `500`, `0`..`5000`) |
| `NBT_AUDIO_STAGE`, `NBT_MEDIA_RATE`, `NBT_ROUTE_MODE`, `NBT_ROUTE_MEDIA`, `NBT_ROUTE_NAV`, `NBT_ROUTE_SINK` | A2I pipeline (`NBT_AUDIO_BT=0` only). Stages 0-2 are car-stable but not audible yet. Test parked only |
| `NBT_MIC_DUMP=1` | Dump captured mic PCM |
| `NBT_VID_INSETS=top,bottom,left,right\|off` | Phone UI insets, calculated from the video canvas and panel size: on 1280x480 `0,240,0,0` at 720p and `0,180,0,0` at 480p. MINI round display (`1180_480` coding) side insets: 40 px at 720p (car-verified on a MINI F60), 25 px at 480p. `off` keeps the manifest's insets |
| `NBT_TOUCH_OX` / `NBT_TOUCH_OY` | Touch offset in canvas pixels (default 0; MINI `1180_480` round display: x -20 at 720p, tester-verified on a MINI F60; -12.5 at 480p) |
| `NBT_VID_DPI` | Video DPI (default 140 at 720p, scaled with the canvas: 88 at 480p, 210 at 1080p; MINI `1180_480` round display 152 / 95 / 228; `0` = phone default). Higher = bigger picture but less room: Android Auto gets 480 x 160 / DPI dp of height (549 dp at 140, 480 dp at 160). From about 150 up the rail can run out of height and the mic slides under the 4th app icon; `NBT_RAIL_HIDE=all` gives it back some room |
| `NBT_RAIL_HIDE` | Ask the phone not to draw its clock, phone signal and/or battery level at the top of the rail, so the rail icons get more height: `all`, or any of `clock,signal,battery` (default: unset, all shown). The number of rail icons itself is the phone's choice; the head unit cannot set it |
| `NBT_VID_RES` | Video profile: `1` = 800x480, `2` = 1280x720 (default), `3` = 1920x1080. Unset or invalid values use `2`. Sets what the phone renders, the decoder size and the touch coordinates together. `1` is car-verified on an F22 (softer, but Google Maps' 3D buildings run more smoothly); `3` crashed a 2 GB head unit and the phone only sends 1080p on 5 GHz Wi-Fi, so it is an experiment |
| `NBT_VID_MW`, `NBT_VID_MH` | Manual video margin width / height overrides (0 by default at 480p / 1080p) |
| `NBT_VID_SRC_H`, `NBT_VID_DST_H` | Manual decoder / renderer height overrides. Defaults follow the video profile and panel size |
| `NBT_VIDEO_DUMP=1` | Raw H.264 to `/tmp/aa_video.h264` (4 MB cap) |
| `NBT_MMR_WAIT_S` | Wait for the MM-Renderer at start (default 90) |
| `NBT_MMR_SPAWN` | Start the MM-Renderer ourselves when none is running (default 1, 0 = never): at once on a car without CarPlay coded (no `/var/opt/conn/carplay/carplay.ini`), where the head unit never starts it; with CarPlay coded, only if none is running `NBT_MMR_BOOT_S` after boot (CarPlay coded but not active, e.g. its activation removed by a software update). A running one is never started twice. One we start is stopped when nbt-aad exits; its pid is kept in `/dev/shmem/nbt-aad-mmr.pid` so a restarted nbt-aad can stop it too |
| `NBT_MMR_BOOT_S` | With CarPlay coded: how long after boot the head unit's own MM-Renderer is waited for before nbt-aad starts one (default 60; the Supra's is up ~23 s after boot) |
| `NBT_PURGE_ALL=1` | Destroy every MM-Renderer context, not only `aadvideoctx*`/`cpvideoctx*` |
| `NBT_TOUCH_SX/SY/OX/OY`, `NBT_TOUCH_W/H`, `NBT_TOUCH_IDLE_MS`, `NBT_TOUCH_CFG_H`, `NBT_TOUCH_PORT` | Touch scale/offset, clamp (1280x720), stuck-finger release (400 ms), declared height (720), UDP port (30567) |
| `NBT_TOUCH_PROBE=1`, `NBT_TOUCH_PROBE_SECS` | Log raw touch frames only |
| `NBT_KNOB_RAW=1` | Log every raw controller message |
| `NBT_MOST_LOG=1` | Log what the MOST client sees, tagged `[MOSTLOG]`: the registry, then for each message type the first 3 and then payload changes, at most 200 lines per type. For working out the car's signals from a drive. With `NBT_KNOB=0` too, the client still starts, for the log alone. The old `NBT_CCS_DEBUG=1` / `NBT_CCS_ALL=1` switch this on |
| `NBT_GEAR_REVERSE` | Gear value for R (default 2; car codes 3 P, 1 N, 2 R, 5 D) |
| `NBT_NEUTRAL_RESUME_S`, `NBT_GEAR_NEUTRAL` | Manual gearbox: seconds in neutral straight after reverse before Android Auto comes back over the park-assist view (default `5`; `0` = off). Leaving neutral or going back into reverse cancels it; not after you exited Android Auto. Gear value for N (default 1). Log: `[Gear] ... reverse -> neutral`. Not car-verified |
| `NBT_REVERSE_FOCUS=1`, `NBT_BACK_HOLD_FOCUS=1` | Also tell the phone it lost the screen in reverse / on BACK hold |
| `NBT_CP_HANDOFF=0` | Turns off the CarPlay hand-off (screen window and decoder created at start and never retuned for CarPlay, as before v1.8.32) |
| `NBT_CP_COMPAT` | `1` / `0` overrides the **CarPlay compatibility** Settings checkbox (`/var/opt/aa/carplay-compat`), read at start (v1.9.57) |
| `NBT_CP_YIELD`, `NBT_CP_PURGE`, `NBT_CP_WINGRP` | `1` / `0` override CarPlay compatibility for one fix: yield to CarPlay completely, destroy (1) or detach (0) the factory CarPlay decoder context, CarPlay's window in our window group (1) or top-level (0) |
| `NBT_CP_LIVE_RETUNE=0` | Do not give the decoder CarPlay's settings when CarPlay takes the screen during a live Android Auto session (v1.8.37) |
| `NBT_PTT_ASSISTANT=1` | Experimental: long press of the steering-wheel voice button starts the Google Assistant. Off by default (the button is only logged) |
| `NBT_MFL_NEXT`, `NBT_MFL_PREV`, `NBT_MFL_PLAYPAUSE` | Steering-wheel buttons (MOST 15/02) that become Android's next / previous / play-pause media keys: `fkt` or `fkt:value`, hex as the recon log prints them (`511:2`), several separated by commas (`511:2,C31`). A `:value` maps one direction of a roller that carries both on one function; without it any change away from 0 is a press. Defaults (`511:2` next, `511:1` previous) come from a J29 log and are unconfirmed - swap them if the wheel skips the wrong way, and `NBT_MFL_*= ` (empty) unmaps a key. Only forwarded with **Media Controls for LCI Steering Wheels** (`NBT_LCI_MEDIA`) on and `NBT_AUDIO_MODE` not `bt`. `NBT_MFL_LOG=0` stops the per-change log line. See `car/mfl.hpp` |
| `NBT_AA_BROWSE=1` | Experiment, off: advertises the Android Auto media browser service (channel 17, descriptor field 11) with an empty config and logs every message the phone sends there. Nothing else uses it. See the v1.9.37 entry |
| `NBT_GPS_ACCURACY_M` | A fixed accuracy for every car GPS fix, in metres, 2 to 499 (default: HDOP x 5 m, at least 2 m; 10 m when a fix has no usable HDOP) |
| `NBT_NIGHT_MODE=0/1`, `NBT_DRIVING_STATUS=0` | Sensor values. Night mode follows the car by default (`vehicle.dayNightChangeOver` via the iDrive app); `NBT_NIGHT_MODE` forces day (0) or night (1) |
| `NBT_CAR_MAKE/MODEL/YEAR`, `NBT_VEHICLE_ID`, `NBT_HU_MAKE/MODEL`, `NBT_SW_VERSION`, `NBT_BT_ADDR` | Single identity fields |
| `NBT_DRIVER_SIDE` | `left` or `right`: the driver's side Android Auto is told (default: left; nothing reads it from the car) |
| `NBT_CARPLAY_INI` | Read a different `carplay.ini` (never the factory template in `/opt/conn/data/carplay/`) |
| `NBT_CTL_PORT` | Control port (default 52790) |
| `NBT_KOMBI_NAV` | `1` / `0` forces the cluster/HUD arrows on / off, whatever the menu checkbox says (default: the checkbox) |
| `NBT_KOMBI_NAV_APPROACH` | When a maneuver shows: `time` (default; by the phone's seconds to it), `distance` (within `NBT_KOMBI_NAV_APPROACH_MI`, hidden again a little further out) or `off` (at once) |
| `NBT_KOMBI_NAV_WARN_S`, `NBT_KOMBI_NAV_MAIN_S` | `time`: seconds before the maneuver it shows (default `50`, `10`..`120`) and is shown again (default `20`, `0`..`60`, below the first; `0` = never again). Shown again turns the 6WA's nav panel back on, if it was dismissed with the stalk button |
| `NBT_KOMBI_NAV_MWAY_WARN_M`, `NBT_KOMBI_NAV_MWAY_MAIN_M` | `time`: off-ramps and forks also show this many metres before them (default `2000`) and again at (default `1000`), `200`..`5000` |
| `NBT_KOMBI_NAV_APPROACH_MI` | `distance`, and `time` when the phone sends no seconds: show a maneuver only within this many miles (default `1`, `0.1`..`9.9`; `0` = at once, and on its own it turns the approach `off`). Set, it wins over the **Show directions at any distance** checkbox |
| `NBT_NO_LOG` | `1` turns every log sink off (daemon RAM + USB log, stdout/stderr, app log and slog), the same as ticking **Disable logging (performance test)**; it wins over the checkbox. Useful to measure whether logging costs anything; the one-shot `--pf-*` / `--license` commands keep their output |
| `NBT_KOMBI_NAV_SHOW_BAR=1` | Keep the cluster's red distance bar (hidden by default: without an iDrive route its percentage means nothing) |
| `NBT_KOMBI_NAV_YIELD=0` | Don't watch the system log for iDrive routes |
| `NBT_KOMBI_NAV_LANES=0` | No lane row in the cluster / HUD (the arrows stay) |
| `NBT_HUD_JUNCTION=0` | 6WA cluster (`NBT_CLUSTER=6wa`): keep the arrow all the way to the turn. By default the arrow gives way to a junction view at the main prompt (see `NBT_KOMBI_NAV_MAIN_S`) or within `NBT_HUD_NEAR_M`, like the factory navigation's near view: the maneuver's roads (in and out, both branches of a fork or an exit, a roundabout's ring and exit number) with the route drawn over them, its tail shorter in four steps on the way in. Android Auto sends no map, so other side roads are not drawn |
| `NBT_HUD_NEAR_M` | 6WA: the junction view comes in this close to the maneuver whatever the time (default `300`, `50`..`2000`) |
| `NBT_HUD_LANES=1` | 6WA: the phone's lanes in the cluster's lane layer, left of the picture. Off by default: where the cluster shows that layer is not known yet |
| `NBT_KOMBI_NAV_INPLACE` | How the next maneuver (or a lane change) is sent on a route already shown: `1` (default) = an in-place update of the shown maneuver, the cluster's own update block, so it doesn't rebuild the route; `2` = the same without the guiding model; `0` = the whole route graph again. A guidance gap (rerouting) always sends the whole graph |
| `NBT_KOMBI_NAV_HANDLE`, `NBT_KOMBI_NAV_HOST`, `NBT_KOMBI_NAV_PORT` | KOMBI guidance instance handle (default `0xC8`, the same on every boot seen), address (`160.48.199.96`) and port (`30502`) |
| `NBT_DIAG=1`, `NBT_VERBOSE` | Window/LayerManager dump 8 s into a session; verbose protocol log |

Old "squashed" picture mode: `NBT_VID_INSETS=off`, `NBT_VID_SRC_H=480`, `NBT_VID_DST_H=480`, `NBT_TOUCH_SY=1.5`.

### Browser audio mode

Car-verified; the default in v1.8.61..v1.8.65. Since v1.8.66 Bluetooth is the default again: put
`NBT_AUDIO_MODE=browser` in `nbt-aad.env` to use browser audio.

- The phone gets three audio sinks: media (ch 9, 48 kHz stereo), guidance (ch 8, 48 kHz mono only, see
  `NBT_GUIDANCE_RATE`: nav prompts, Assistant speech, message read-outs) and a new system-audio sink (ch 16, 16 kHz mono: notification chimes, UI
  sounds). Media then no longer goes to Bluetooth A2DP.
- Each stream start names the format the phone picked from the offers (`configuration_index`); the daemon converts
  from that rate from the first frame. Only a start without a usable index falls back to measuring the rate from the
  timestamps, and then only among the rates offered for that stream.
- The daemon mixes the three streams and ducks the media under prompts and system sounds (`NBT_DUCK_*`).
- It plays the mix as one more client of the car's browser audio (`/opt/mm/lib/libasound/libasound.so` ->
  `dev-libasound` -> A2I `input_stereo_3`) and asks for the browser audio source with `audioconn -b 0xb8`. The
  CarPlay sources can't be used: they refuse to allocate unless an iPhone is connected.
- `0xb8` is an *interrupting* source (like the Owner's Manual audio): while it is built the radio or Bluetooth source is
  paused and iDrive's Media menu still shows it. The daemon builds it when Android Auto first makes a sound. Once AA
  media has played it is kept through pauses and released after `NBT_BROWSER_IDLE_S` (10) s of silence; when only a
  prompt or a system sound played (you are listening to the radio) it is released `NBT_BROWSER_PROMPT_IDLE_MS` (1000)
  ms after it, so the radio is cut only for the prompt. Audio that arrives before the car grants the source waits, so
  the start of a prompt is not cut off.
- Calls are unchanged (Bluetooth hands-free). Whether `0xb8` comes back by itself after a call is not known yet.

What to look for in the log: `[Main] audio mode: browser`, `[SDR] added channel 16`, `[AASession] ch N audio sink
offers: ..`, `[AASession] ch N audio start: config K = .. Hz`, `[Browser] PCM open: card ..`,
`[Browser] device setup: ..`, `[Browser] connection 0xb8 granted`, `[Browser] media ducked under a prompt ..`, and the
10 s `[Browser] blocks ..` statistics line (queued / dropped bytes per stream, route state, write errors) with its
`[Browser] gaps ..` line: mixer blocks of a playing stream that got only part of their audio (short) or none (empty)
and were padded with silence, the lowest buffer fill in the interval, and the session's total gaps.
Handy checks from the shell: `audioconn -l` (is `0xb8` listed as `BrowserFront`?) and `sloginfo | grep -i preAllocate`.

#### Navigation prompts over the radio

In browser mode the guidance channel (nav prompts, Assistant replies) no longer goes through the browser mix, which
mutes the radio. Nothing to set: `NBT_SPEECH` is on by default there. `NBT_SPEECH=0` goes back to the browser mix.

The defaults are the nav prompter of the F30 (NBTevo_S19322I): `NBT_SPEECH_EP=/input_mono_0`, `NBT_SPEECH_SRC=0x1a`,
`NBT_SPEECH_SINK=0x1`. Set them only for a car that differs. `NBT_SPEECH_SRC=0` keeps the prompts in the browser mix.

Finding the values on another car, over SSH:

- Endpoint: `grep -i announcement /opt/speech/bin/natp.cfg`, the line with NAVI. On the F30
  `input_mono_0 … Prompter Announcement NAVI`, set with a leading slash: `/input_mono_0`.
- Source and sink: `audioconn -l | grep NAV_AUTO`. On the F30
  `ConnId=0x58:  [0x1a] eSRC_NAV_AUTO - [0x1] eSNK_SPEAKERS`: the source is `0x1a`, the sink `0x1` (not the
  `ConnId`, the car refuses that one).
- Check by ear with the radio on: `audioconn -b <src> <sink>` should lower the radio, `audioconn -r <src> <sink>`
  bring it back. If it doesn't come back, a head unit restart does.

- Prompts play on the endpoint, the car's nav prompter. For each prompt the daemon asks for the nav focus
  (`audioconn -b 0x1a 0x1`), which ducks the radio like the car's own navigation. Audio waits for the grant.
- Car-tested on the F30 (test builds v1.9.77-pr209 and v1.9.79-pr209, including a 30-minute drive): prompts and
  Assistant replies duck DAB and FM, AA media is ducked, not muted, and the car's own navigation speaks between AA
  prompts.
- The endpoint is opened at each prompt start and closed once the focus is given back, so the car's own navigation
  can speak between AA prompts. When the car's prompter takes the endpoint during an AA prompt (both navigations at
  once, or the sample in the iDrive volume menu), ours stops taking audio: after 1 s (3 s while it is still opening)
  the rest of the prompt is dropped and the focus given back. After 3 failed opens in a row prompts use the browser
  mix until the session ends.
- The focus is released after the queued audio plus `NBT_SPEECH_HOLD_MS`. The focus request is one `audioconn` run,
  not retried (`NBT_AUDIOCONN_RETRIES` does not apply). No grant within 2 s: the prompt is dropped and the next prompts
  use the browser mix for 30 s; after two in a row, until the session ends. The rate the phone declared is used.
- While AA media plays (Tidal, Spotify, ..: the browser source is built for it, or media played within
  `NBT_BROWSER_IDLE_S`), prompts stay in the browser mix with the media ducked by the daemon's mixer (`NBT_DUCK_DB`):
  under the nav focus the car would mute the browser source, media included.
- If the speech path can't take a prompt (`NBT_SPEECH_SRC=0`, endpoint missing or not open, no `audioconn`, no
  declared rate, backing off) the prompt plays through the browser mix (`0xb8`) as before, radio muted for it. AA
  media and system sounds always stay on `0xb8`. The volume pill is not shown for volume changes while the nav focus
  is being taken or released and for 1.5 s after.
- Log: `[Browser] guidance: nav prompt channel /input_mono_0 focus 0x1a -> 0x1 (NBT_SPEECH=1)` (else `guidance: browser mix ..`),
  `[Speech] ready: .. opened for each prompt, route audioconn 0x1a -> 0x1`, per prompt `[Speech] prompt #N started: ..`
  and `focus granted after .. ms, released .. ms after the last audio`, then `[Speech] endpoint closed`; on a refused
  focus `focus not granted after .. ms: dropped, no focus requests for .. s` (the second in a row adds `browser mode
  keeps prompts in its mix until the session ends`); on a taken endpoint `the endpoint took
  no buffer for .. ms`; with AA media `[Browser] AA media is on the browser source: nav prompt stays in the mix`.
- Bluetooth mode: `NBT_SPEECH` works there too, with the declared rate (16 kHz mono if none given), on
  `/input_stereo_AirPlayAlt` unless `NBT_SPEECH_EP` is set. Without `NBT_SPEECH_SRC` (default `0` there) prompts play at
  once, without a focus. During a backoff prompts are dropped (no mix to fall back to). The car's Bluetooth source is
  requested again when the mic closes (`NBT_BT_REFOCUS`), which switches away from the radio.
- Only car-tested on the F30. Testers with other cars (Supra, MINI, G-series) are welcome: the endpoint name and the
  source id may differ there (see above). Without the endpoint the start-up open fails (`[Speech] could not open ..`)
  and prompts stay in the browser mix.

