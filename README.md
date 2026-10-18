# Stride — your road to 7K

A mobile-friendly personal fitness tracker for 1 November 2026–1 February 2027.
Daily checkboxes, water logging, running and home-strength sessions, steps,
sleep, protein habits, a journal, progress views, and calendar reminders.

## Deploy from GitHub to Netlify

1. Push this repository to GitHub.
2. In Netlify, add/import a project from GitHub and select this repository.
3. Use the `main` production branch. Leave the build command blank.
4. The included `netlify.toml` sets the publish directory to `dist`.
5. Keep Netlify's Free plan selected and follow the project screen to publish.

Once this repository is linked to a Netlify project, pushes to the configured
production branch trigger deployments. Linking a GitHub account alone does not
link every repository automatically.

No build step, API key, backend, database, or ChatGPT subscription is required.
Use the provided Netlify address; buying a custom domain is optional.

## Run locally

Serve the `dist` directory with a static HTTP server. For example:

```sh
python3 -m http.server 8080 --directory dist
```

Open `http://localhost:8080`. Opening index.html directly as a file is not
supported because the app uses JavaScript modules.

## Validation

With Node.js installed:

```sh
node --check dist/app.js
node --check dist/core.mjs
node --test tests.mjs
```

Tests cover the dated training progression, recovery goals, local-log handling,
calendar export, backup validation, and basic render/interaction logic.
Real mobile notification delivery depends on device and calendar settings.

## Data and reminders

Logs stay in the current browser's localStorage. They do not sync between
devices and are not sent to GitHub or Netlify. Export a backup regularly.
On changing website addresses, export from the old site and restore on the
new one using Settings. The exported JSON contains personal fitness data;
do not commit backups to this repository.

For reminders while the app is closed, import the generated .ics calendar
and enable notifications in the calendar app. Open-page browser reminders
are optional and are not background push notifications.

The hosted application can be visited by others, but their browser does not
receive your local logs. There is no application-level account/login system.

## References

- https://docs.netlify.com/build/configure-builds/file-based-configuration/
- https://www.netlify.com/pricing/

## Samsung Health (personal-use Android app)

The free Android 14+ app is available under **Settings → Samsung Health** on the
website, with source and build instructions in `android/README.md`. It reads
Samsung Health through Health Connect, on demand, with three read permissions.
The browser and installed app keep separate logs; use backup export/restore to
move between them. No paid backend, subscriptions or health uploads.

Run all checks with `node --test tests.mjs health-tests.mjs`. The APK is compiled
and signed, but still requires Samsung phone verification.

## Android 1.1: live tracking

The signed Android download now includes outdoor GPS run/walk sessions, an opt-in
all-day phone step counter and local goal-completion notifications. Open Today →
Open live tracking. Set your own name and step target in Settings; every phone
stores an independent profile with no account or paid backend. Native live history
is separate from planner/Samsung imports and has its own export. See
[Android setup and limitations](android/README.md). Actual device testing remains
required; host tests cannot validate Samsung sensor/battery behavior.

## Android 1.2 and refreshed web UI

Hydration reminders are optional local Android notifications scheduled with
inexact alarms. Strength mode gives guided sets and written exercise tutorials.
Progress combines Samsung Health, live and manual activity records, with
conservative duplicate checks. Finished live sessions are included in the
regular JSON backup. The responsive UI uses Stride's own forest-green and warm-neutral visual style,
with clear actions and comfortable spacing.
The Android package has no Internet permission or paid backend.

## Android 1.3 visual update

Restores Stride’s forest-green palette with warmer surfaces and a clearer
workout layout. Functionality and package identity remain the same. Install
over the earlier Android version to preserve local data.
