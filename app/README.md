# CF LOCK

Kiosk-style device latch for Android 7.0 (API 24) through Android 16 (API 36).
One screen, one numeric field, one accepted value. Nothing on the device is
deleted, encrypted, or wiped: the app takes the surface, hides the bars, and
will not step aside until the right code is typed.

Kotlin only, no Compose, no AndroidX, no Gradle. `app/build.sh` drives
kotlinc -> D8 -> aapt2 -> apksigner and drops a signed APK in `app/dist/`.

## Layout

```
app/
  AndroidManifest.xml        package com.cf.kiosk
  build.sh                   headless build, 6 stages
  src/com/cf/kiosk/
    Gate.kt                  key check: SHA-256(salt:input) against four masked 64-bit limbs
    Prefs.kt                 locked / fails / boots / cooldown, commit() so it survives a kill
    Shield.kt                immersive flags, lock task, wake, home-role probe
    LinkActivity.kt          launcher: admin, home slot, overlay grant, arm
    LockActivity.kt          the block screen
    OverlayGuard.kt          window 2038 surface, the layer above the shade
    KeepAliveService.kt      foreground anchor
    BootReceiver.kt          boot / package-replace / 15 min tick re-arm
    GuardReceiver.kt         device-admin surface
    ScreenReceiver.kt        panel wake re-assert
  res/                       icons (CF monogram), strings, admin policy
  tools/make_icons.py        regenerates every density + adaptive pair
  dist/CF-LOCK.apk           signed output
```

## Build

Needs a JDK 11+ and five jars/binaries: `kotlin-compiler.jar`, `android.jar`,
`d8.jar`, `apksigner.jar`, `aapt2`. Paths are environment overrides.

```sh
export JAVA=/path/to/jdk/bin/java
export KOTLINC_JAR=/path/to/kotlin-compiler.jar
export KOTLIN_STDLIB=/path/to/kotlin-stdlib.jar
export COROUTINES=/path/to/kotlinx-coroutines-core-jvm.jar
export ANNOTATIONS=/path/to/annotations-13.0.jar
export ANDROID_JAR=/path/to/android.jar
export D8_JAR=/path/to/d8.jar
export APKSIGNER_JAR=/path/to/apksigner.jar
export AAPT2=/path/to/aapt2
export KEYSTORE=/path/to/release.jks KS_PASS=... KEY_PASS=... KEY_ALIAS=...

bash app/build.sh
```

`aapt2 compile` runs with `--dir res`, so the resource tree must exist before
the build. `app/tools/make_icons.py` writes it from scratch if you wiped it.

## Install and arm

```sh
adb install -r app/dist/CF-LOCK.apk
adb shell appops set com.cf.kiosk SYSTEM_ALERT_WINDOW allow   # optional shortcut
adb shell dpm set-active-admin com.cf.kiosk/.GuardReceiver    # optional shortcut
```

Open CF LOCK, walk the four buttons (admin, home slot, overlay grant, arm).
The status line under the buttons reads `admin ok | home ok | overlay ok`
when each one took.

## How the latch holds

| Layer | Mechanism | Held against |
|---|---|---|
| Lock screen | `LockActivity` singleInstance, immersive flags, re-assert every 1.5 s | back, recents, home, notification press |
| Overlay | `TYPE_APPLICATION_OVERLAY`, full-screen, `FLAG_SECURE`, `FLAG_NOT_TOUCH_MODAL` | the notification shade and heads-up banners |
| Home slot | `CATEGORY_HOME` on the lock activity, lock task via admin | launcher, task switcher |
| Boot | `BOOT_COMPLETED` receiver starts the foreground service | reboot |
| Watchdog | `AlarmManager` inexact repeat, 15 min | service kills, long idle |
| Uninstall | device-admin active | Settings > Uninstall Apps |

## Releasing the device

Type the code, tap Desbloquear. On success the app clears the lock flag,
stops the overlay and the service, drops lock task, clears its preferred-home
binding, and sends you to the home picker. Nothing else is touched.

If the admin bit is still active, remove it in Settings > Security > Device
admin apps > CF LOCK.

## Recovery without the code

The app never encrypts or deletes anything, so recovery is always the same
shape: data stays, access is what is held.

- **Preferred-home path.** If the lock activity never got bound as home, an
  ADB shell can uninstall or force-stop it:
  `adb shell pm clear com.cf.kiosk` resets the lock flag.
  `adb shell pm disable-user --user 0 com.cf.kiosk` stops it from launching.
- **Overlay path.** `adb shell appops set com.cf.kiosk SYSTEM_ALERT_WINDOW deny`
  removes the shade-covering layer, then `pm clear` finishes the job.
- **Lock task path.** `adb shell am force-stop com.cf.kiosk` plus
  `adb shell cmd device_policy` on the admin, or a reboot into safe mode.
- **Worst case, device owner path.** If neither the launcher nor ADB is
  reachable, a factory reset clears the app. That is also the honest limit of
  a user-space kiosk on a non-rooted phone: it can hold the surface, it cannot
  hold the bootloader.

## Coverage notes

- **API 24-25.** `TYPE_PHONE` overlay, no overlay permission needed.
- **API 26-29.** `TYPE_APPLICATION_OVERLAY`, `startForegroundService`, adaptive icon.
- **API 30-33.** Background activity launches restricted; the overlay and the
  foreground service carry the re-assert instead of `startActivity`.
- **API 34-36.** `foregroundServiceType="specialUse"` with
  `FOREGROUND_SERVICE_SPECIAL_USE`; `showWhenLocked` / `turnScreenOn` on the
  lock activity instead of the deprecated attributes.
- Gesture navigation is not a `systemUiVisibility` target. The overlay and lock
  task are what hold it shut; a partial swipe still shows a transient bar.

## Key handling

The accepted code is stored nowhere. `Gate.kt` holds four 64-bit limbs, each
the matching limb of `SHA-256(salt:input)` XOR a fixed mask, and compares with
an accumulating OR so timing does not leak limb position. Verified by
brute-forcing the full 4-digit space: exactly one value passes. Regenerate the
limbs if you change the code:

```python
import hashlib
d = hashlib.sha256(b"cf9f2a71:<new-code>").digest()
for i in range(4):
    w = int.from_bytes(d[i*8:(i+1)*8], 'big') ^ MASK[i]
    print(f"0x{w:016X}UL")
```
