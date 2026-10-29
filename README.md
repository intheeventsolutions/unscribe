# Unscribe

A small, personal Android app that reads epub books aloud in a natural female Australian voice. It runs fully on the phone (no internet, no accounts, no cost) using an open-source neural voice. Open a book, tap where you want to start, press Play, and put the phone in your pocket.

> Status: early development. See [Roadmap](#roadmap).

## Features

- Add `.epub` files from phone storage; the library remembers your place in every book.
- Tap anywhere in the text to choose where reading starts (defaults to the top of the page), then press Play. No distracting highlighting.
- Keeps reading with the screen off; controls on the lock screen, notification, headphones, Bluetooth and car.
- Adjustable speed (0.75x – 2x) without the chipmunk effect.
- Choice of six female Australian voices (Tully by default), or the phone's built-in voice as a fallback.

## Installing on your phone

1. On the phone, open the latest release at <https://github.com/intheeventsolutions/unscribe/releases> and download `unscribe-vX.Y.Z.apk`.
2. Open the downloaded file. Android will ask to allow installs from your browser ("Install unknown apps"); allow it once.
3. Tap **Install**. Future versions install over the top and keep your library, because every release is signed with the same key.

Requirements: Android 8.0 or later, 64-bit ARM phone (practically every phone from the last several years), about 250 MB free (the APK is ~120 MB, mostly the voice).

## Using the app

1. Tap **+** and pick an epub.
2. Tap the book to open it. It opens where you left off.
3. Optionally tap a sentence to start reading from there; otherwise reading starts at the top of the page.
4. Press **Play**. Lock the phone if you like; use the notification, headphones or car controls to pause.
5. Use the speed slider in the player bar, and **Settings** to change voice.

## Development setup (Ubuntu 24.04)

You need: a JDK (17 or 21), the Android SDK command-line tools, Python 3 (for preparing the voice model), `git`, and the GitHub CLI (`gh`). Android Studio is optional.

### 1. Java and base packages

```sh
sudo apt update
sudo apt install openjdk-21-jdk-headless unzip python3-venv git
javac -version   # must print a version; the JRE alone is not enough
```

### 2. Android SDK (command-line tools only)

```sh
mkdir -p ~/Android/Sdk/cmdline-tools
cd /tmp
# Get the current "Command line tools only" Linux zip name from https://developer.android.com/studio
curl -LO https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip
unzip commandlinetools-linux-*_latest.zip
mv cmdline-tools ~/Android/Sdk/cmdline-tools/latest
```

Add to `~/.bashrc`, then open a new terminal:

```sh
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH
```

Install the SDK packages:

```sh
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

### 3. Phone over USB

```sh
sudo apt install android-sdk-platform-tools-common   # udev rules so adb can see the phone
```

On the phone: **Settings → About phone → tap Build number 7 times**, then **Settings → System → Developer options → USB debugging** on. Plug in, run `adb devices`, and accept the prompt on the phone.

### 4. Clone and build

```sh
git clone git@github.com:intheeventsolutions/unscribe.git
cd unscribe
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # only needed if ANDROID_HOME isn't set

# One-off: download the Australian voice and convert it for sherpa-onnx (~80 MB download)
python3 -m venv ~/.venvs/unscribe && ~/.venvs/unscribe/bin/pip install onnx
~/.venvs/unscribe/bin/python scripts/prepare_voice.py

./gradlew test assembleDebug   # also downloads the sherpa-onnx library into app/libs on first run
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Optional: Android Studio

```sh
sudo snap install android-studio --classic
```

Open the project folder; it will reuse `~/Android/Sdk`.

## Project layout

```
app/src/main/java/au/com/intheevent/unscribe/
  epub/       epub parsing (spine, table of contents, chapter text)
  text/       sentence splitting and text clean-up for speech
  tts/        speech engines (on-device Piper voice, system voice fallback)
  playback/   background playback service and media controls
  data/       library, reading positions, settings
  ui/         Compose screens
scripts/      one-off tooling (voice model preparation)
```

## Workflow

- Work happens on `feat/...` or `fix/...` branches, merged to `main` via pull request.
- GitHub Actions runs the unit tests and builds a debug APK on every PR (download it from the run's **Artifacts**).

## Releasing

One-time: create a signing key and store it in GitHub secrets. Keep the `.jks` file and passwords somewhere safe; losing them means reinstalling from scratch (and losing the library).

```sh
mkdir -p ~/keys
keytool -genkeypair -v -keystore ~/keys/unscribe.jks -alias unscribe -keyalg RSA -keysize 4096 -validity 36500
gh secret set KEYSTORE_B64 --body "$(base64 -w0 ~/keys/unscribe.jks)"
gh secret set KEYSTORE_PASSWORD
gh secret set KEY_ALIAS --body unscribe
gh secret set KEY_PASSWORD
```

Each release: bump `versionCode`/`versionName` in `app/build.gradle.kts`, merge, then

```sh
git tag v0.1.0 && git push origin v0.1.0
```

CI builds the signed APK and attaches it to a new GitHub release.

To build a signed release locally, export `UNSCRIBE_KEYSTORE`, `UNSCRIBE_KEYSTORE_PASSWORD`, `UNSCRIBE_KEY_ALIAS` and `UNSCRIBE_KEY_PASSWORD`, then run `./gradlew assembleRelease`.

## Roadmap

1. Project setup, CI, README
2. On-device Australian voice (Piper model via sherpa-onnx) with a test screen
3. Epub parsing, library and reader screens
4. Background playback, media controls, tap-to-start, remembering your place
5. Speed and voice settings, audio focus, signed releases

## Credits and licences

- **Voice:** [en_AU-librivox-medium](https://huggingface.co/DataCraftsmanAustralia/piper-en_AU-librivox-medium) by DataCraftsmanAustralia, trained on public-domain LibriVox recordings, licensed CC-BY-4.0.
- **Speech engine:** [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) (Apache-2.0), running a [Piper](https://github.com/rhasspy/piper) VITS model.
- **Pronunciation data:** [espeak-ng](https://github.com/espeak-ng/espeak-ng) (GPL-3.0), shipped as data inside the app.
- Unscribe is a personal project, not published to any app store.
