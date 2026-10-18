# Stride for Android 14+

Personal-use, offline Android version of the Stride tracker. No subscriptions,
paid APIs, server, cloud database, or Play Store listing is required for this
personal build. You install the signed APK on your own compatible phone.

## Live tracking (version 1.1)

Install the update over version 1.0 without uninstalling. The same signing key and
package preserve existing planner and Samsung Health data. In Settings, change your
name and step goal; each phone stores an independent profile, with no login/server.
Open **Today → Open live tracking** (also available in Settings).

- Enable all-day step counting and grant Physical activity + notification access.
  Counting starts from enable time, not midnight retrospectively. Keep the phone
  with you. Steps while counting is off are excluded. A cumulative sensor batch
  spanning midnight is discarded because its individual step times are unknown.
- Select Run or Walk and a distance target; Start requests precise location.
  Pause/Resume excludes paused time and distance. Finish saves the session.
- Daily steps and session distance each trigger one goal notification. Test the
  notification from the tracking screen; Android channel settings and Do Not
  Disturb control whether it sounds. Goal completion does not stop a workout.
- An ongoing notification keeps tracking visible. Reboot, force-stop, permission
  revocation or OEM battery restrictions can stop tracking. Reopen and enable step
  counting again. Interrupted workouts recover paused from the last checkpoint.
- GPS is outdoor-only approximate distance, not a route map. Low-accuracy fixes,
  implausible speed and long gaps are excluded. Step counting needs a hardware
  step-counter sensor. Neither feature is guaranteed when the OS kills the app.
- Live history and step totals are separate from planner/Health Connect totals;
  they are never summed together. Export live history from the tracking screen;
  planner backup does not contain native live history. Live export is archival
  JSON (no restore UI yet). GPS coordinates are not persisted.

## Connect Samsung Health

1. In Samsung Health, open Settings → Health Connect. Let Samsung Health write
   steps, exercise and distance, then open Samsung Health to finish syncing.
2. Install the APK and open Stride → Settings → Sync Samsung Health.
3. Choose the three read permissions in Android's Health Connect screen.
4. Keep Stride open while it reads the last seven local calendar days. Review
   the preview and choose Import. Repeat Sync whenever you want an update.

This is user-triggered foreground syncing, not continuous background syncing.
No GPS route or heart-rate permission is requested. Samsung Health is the only
data source (`com.sec.android.app.shealth`). Steps use Health Connect's aggregate
API rather than summing raw phone and watch samples. Run/walk sessions are deduplicated
by record ID. Distance is the Samsung-origin aggregate within each session's time
window; it depends on what Samsung Health shares. Duration is elapsed time including
pauses. Sessions are assigned to their start date in the phone's current time zone.
Sessions starting before the seven-day window are excluded.

Imported step readings replace that day's step total, never add to it. A missing
step reading keeps the manual total. Exercise snapshots replace the previous
snapshot for that day, including removing sessions Samsung no longer shares.
Manual run logs are retained. Synced running sessions take priority in Progress;
walking sessions are displayed separately and aren't counted toward run distance.
Activity checkboxes remain the user's decision.

## Data and backups

The Android app has no Internet permission, no analytics, and no health-write
permissions. All UI assets are bundled; remote navigation, scripts and frames are
blocked. Data is stored in this app's WebView local storage. Android automatic
cloud backups are disabled. File exports use Android's system document picker.

The installed app and browser do not share storage or sync to each other. Export
a Stride JSON backup in one and restore it in the other. Restoring replaces the
destination's logs; export that destination first if you need to keep them. Backups
include personal health records. Clearing app storage or uninstalling removes
local records. Revoking Health Connect permission prevents future reads but does
not erase previously imported records.

## Build and signing

Requires a JDK 17 with compiler modules, Python 3, the official Android SDK 34
platform and official build-tools 34.0.0. No third-party Java libraries.

Set these environment variables to absolute paths, then run `python3 android/build.py`:

- `STRIDE_ANDROID_PLATFORM`: SDK directory containing Android 34 `android.jar`.
- `STRIDE_ANDROID_BUILD_TOOLS`: directory containing `aapt2`, `zipalign` and `lib/`.
- `STRIDE_ANDROID_KEYSTORE`: private signing keystore (PKCS12, alias `stride`).
- `STRIDE_ANDROID_PASSWORD_FILE`: private file containing its password.

Keep the signing keystore and password outside the repository. Reuse the same
key and increase the manifest versionCode to produce an installable update.
The signing key backup belongs to the owner and must not be published.

Output: `android/build/Stride-Android.apk`. Copy the verified APK to
`dist/stride-android.apk` to offer it on Netlify. `netlify.toml` is unchanged.
Netlify does not compile Android code or require a paid upgrade.

## Verification boundary

The release is compiled against Android 34, signed and signature-verified, with
web unit tests covering validation, duplicate sessions, backup round trips and
run totals. No Samsung phone or Health Connect runtime is available in the build
environment; end-to-end device validation is still required. Do not claim that
granting permissions, OEM-specific syncing or on-device export/import was tested.

If no data appears, check both Samsung Health's write permissions and Stride's
read permissions. Open Samsung Health and wait for watch/phone synchronization,
then retry. If any permission is refused or a read fails, no partial import is
applied. Keep Android's security checks enabled during installation.
