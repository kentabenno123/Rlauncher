![RLauncher](docs/header.svg)

# RLauncher

A minimal Android launcher. It is a fork of [Olauncher](https://github.com/tanujnotes/Olauncher) and stays very close to it.
The main addition: **web links appear in the app list and behave like apps.**

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

## Differences from Olauncher

- Links in the app list (see above)
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

License: [GNU GPLv3](LICENSE)
