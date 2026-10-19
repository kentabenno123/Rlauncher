![RLauncher](docs/header.svg)

# RLauncher

A minimal Android launcher. It is a fork of [Olauncher](https://github.com/tanujnotes/Olauncher) and stays very close to it.
Additions: **web links** that behave like apps, **folders** on the home screen, **weather** next to the clock, buttons for **smart plugs** and an optional **homelab status** indicator.

## Links (web shortcuts)

A link is an entry in the app list that opens a web page in your default browser (e.g. Firefox).
It looks like an app and works like one: tap it, rename it, delete it, put it on the home screen or assign it to a swipe gesture.

### Add a link

1. Long-press the home screen → **Settings**.
2. Tap **Add link** at the top.
3. Enter a **Name** (e.g. `Wikipedia`) and a **URL** (e.g. `wikipedia.org`), then tap **OK**.

The link shows up in the app list right away.

- `https://` is optional. For `wikipedia.org` it is added automatically. If you type `http://` yourself, it is kept.
- If you leave the name empty, the domain is used (`wikipedia.org`).
- You can also type a URL into the app search and press **Enter**. This doesn't work for every URL: the search opens an app automatically as soon as only one app matches what you've typed. Adding links through Settings always works.

### Use and edit a link

| Action | How |
|---|---|
| Open | Tap the entry in the app list. It opens in your default browser. |
| Change the URL | Long-press the entry → **URL** |
| Rename | Long-press the entry → **Rename** |
| Delete | Long-press the entry → **Uninstall** |
| Put on the home screen | Long-press a slot on the home screen → pick the link from the list |
| Swipe gesture | Settings → **Swipe left app** / **Swipe right app** → pick the link |

### Requirements

- **RLauncher must be your default launcher.** Only the default launcher can create shortcuts. Otherwise you'll see "Could not add shortcut".
- Android 8.0 or newer.

### How it works

Links are stored as pinned Android shortcuts owned by RLauncher (`Intent.ACTION_VIEW` with the URL).
This way they reuse Olauncher's existing shortcut handling for listing, launching, renaming, deleting and the home screen.
Uninstalling RLauncher removes all links.

## Folders

A folder sits in a home screen slot like an app. Tap it to expand or collapse it with a short animation. Its apps appear indented right below it, and the other entries move down. Folders are always closed when you return to the home screen.

| Action | How |
|---|---|
| Create a folder | Long-press a home screen slot → **+ New folder** at the top of the list → enter a name |
| Add an app or link | Long-press the folder → **Add app** → pick it from the list (hidden apps are included) |
| Remove an app | Expand the folder, long-press the app → **Remove from folder** |
| Rename | Long-press the folder → **Rename** |
| Remove the folder | Long-press the folder → **Remove folder** (the apps stay installed) |

## Weather

The current temperature with a small line icon for the conditions (clear, partly cloudy, cloudy, fog, drizzle, rain, snow, thunderstorm) is shown right-aligned in the clock row. The data comes from [Open-Meteo](https://open-meteo.com), which needs no API key, and is refreshed at most every 30 minutes.

- RLauncher asks once for the approximate location (`ACCESS_COARSE_LOCATION`) and uses the last location the system already knows.
- If no location is available, it uses the fallback coordinates from `local.properties` (see [Configuration](#configuration)). Without either, no weather is shown.
- The weather is hidden together with the clock when date and time are turned off.

## Homelab status

If an [Uptime Kuma](https://github.com/louislam/uptime-kuma) status page is configured, the bottom right corner shows **LAB** with a dot:

- green: the latest check of every monitor on the status page is up
- blinking red: anything else, including when the status page can't be reached

Optionally, a second line above it shows **Gemma** (or any other single service) with a green or red dot, without blinking. It reads one monitor from the same status page by name, and that monitor is left out of the LAB status. This suits services that are often off, like a local LLM on a laptop.

The status is checked when you return to the home screen and every 30 seconds while it stays open.

## Smart plugs

Up to a few round buttons at the bottom of the home screen switch Tuya / Smart Life plugs on and off, so the Smart Life app isn't needed for that. A power button on the far left switches all plugs off at once. Each other circle shows a short label, usually one letter. A filled circle means on, an outline off, and a faded circle means the state isn't known yet.

- The buttons switch through the [Tuya cloud API](https://developer.tuya.com), so the plugs can stay in a separate (guest) network and it also works away from home.
- The state is refreshed when you return to the home screen and every 30 seconds while it stays open. A tap shows the new state right away and reverts it if the cloud refuses.

One-time setup:

1. Create a free account on [iot.tuya.com](https://iot.tuya.com) and a cloud project (type "Smart Home", the data center of your Smart Life account, e.g. Central Europe). Authorize *IoT Core*, *Authorization Token Management* and *Smart Home Basic Service*.
2. In the project, open **Devices → Link Tuya App Account** and scan the QR code with the Smart Life app.
3. Copy the **Access ID** and **Access Secret** from the project overview and the device ids from **Devices → All Devices** into `local.properties` (see below).

The free plan has to be extended on the Tuya platform every few months, otherwise the buttons stop working.

## Configuration

Personal settings are read from the git-ignored `local.properties` at build time, so they never end up in the repository:

```properties
# Uptime Kuma status page; leave out to hide the LAB indicator
rlauncher.kumaStatusUrl=https://status.example.com/status/home
# Monitor on that status page shown as its own line above LAB; leave out to hide it
rlauncher.gemmaMonitor=Gemma
# Tuya cloud project for the smart plug buttons; region: eu, us, cn or in
rlauncher.tuyaAccessId=your-access-id
rlauncher.tuyaAccessSecret=your-access-secret
rlauncher.tuyaRegion=eu
# Plug buttons as label:deviceId, in display order
rlauncher.plugs=L:bf0123456789abcdefghij,T:bf9876543210zyxwvutsrq
# Weather fallback as "latitude,longitude" when no location is available
rlauncher.weatherFallback=52.5200,13.4050
```

Rebuild the APK after changing these values.

## Differences from Olauncher

- Links in the app list, folders, weather, smart plug buttons and homelab status (see above)
- App ID `app.rlauncher`, so it can be installed alongside Olauncher
- The app is called RLauncher
- **Apps: larger tap area** option (Settings → Home screen, taken from Olauncher CF). When it's on, you can tap anywhere in a home screen entry's row, not just on its label.
- No promotional or reminder popups (Pro version, rating, sharing, wallpapers, New Year greetings) and no ads for other apps in Settings

## Installation

There is no store release. Build the APK yourself (requires JDK 17 and the Android SDK):

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Without a cable: copy `app-debug.apk` to your phone, tap it and allow "Install unknown apps".
Then choose RLauncher under **Settings → Apps → Default apps → Home app**.

## Credits & License

RLauncher is based on [Olauncher](https://github.com/tanujnotes/Olauncher) by [tanujnotes](https://github.com/tanujnotes). All other features and the design come from there.

Weather icons: [Lucide](https://lucide.dev), [ISC License](LICENSES/lucide.txt).

License: [GNU GPLv3](LICENSE)
