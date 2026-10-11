# SmartCam

SmartCam is a wellness app that reads your vitals from a selfie video and then helps you act on them. It works out your stress from your face, suggests a calming game and plays music for your mood. It also tracks food, steps, sleep and screen time, and answers questions about your day.

- **Android app** (`android/`): native Kotlin + Jetpack Compose. This is the main product.
- **Website** (`web/` + `server/`): the desktop version. It shares your account and food log with the phone.

```
android/      native Android app (package com.hack.smartcam)
web/          website (HTML/CSS/JS), served by the server
server/       Node server: holds the keys, proxies Gemini + ElevenLabs, talks to Tiger Data, serves web/
db/           Tiger Data table definitions
music/        mood music, one folder per stress level (packed into the app)
SmartCam.apk  latest build, ready to install
```

---

## Technologies we used, and where

### Presage SmartSpectra: vitals from the camera

**What it does in SmartCam:** a 35-second face scan in the **Camera** tab measures:

- pulse
- breathing rate
- heart-rate variability
- facial expression

We turn the HRV into a 0–100 stress score (Baevsky stress index) and the expression into a mood. The stress level then decides three things:

- which game is suggested (high → Meditation, moderate → Jigsaw, low → Tetris)
- how that game is tuned
- which music plays

After the scan the camera keeps running in a small window, with a live pulse logged every 5 seconds to the 24-hour heart-rate chart.

| Where | What |
|---|---|
| `android/.../vitals/Presage.kt` | Starts the SmartSpectra SDK and turns its metrics into `Vitals` (pulse, breathing, HRV, stress, mood). |
| `android/.../ui/screens/CameraScreen.kt` | The scan screen: oval progress ring, fun facts, results and suggested game. |
| `android/.../ui/screens/HomeScreen.kt` | Heart-rate and stress widgets built from the readings. |

### Google Gemini: the app's brain

Gemini does six jobs. Each one is limited to SmartCam: the chat refuses anything that isn't about the app or your own health data, and the shop only ever returns products.

| Job | Where | How |
|---|---|---|
| **Ask Gemini** chat | `ui/screens/ChatScreen.kt`, `web/js/views/chat.js` | The system prompt includes your steps, latest scan, today's food, screen time, sleep and alarms, plus a guide to where every feature lives, so how-to answers are accurate. Off-topic questions get one friendly refusal. |
| Food photos | `ui/screens/FoodScreen.kt` | Snap a plate and Gemini (vision) estimates calories, protein, carbs, sugar, fat and salt as JSON. You check the numbers before saving. |
| Tomorrow's plan | `data/Health.kt` → `makePlan` | Uses your profile, what you ate, what you burned and your recent chats. Returns JSON with daily limits and a timed plan (meals, exercise, habits). The limits then drive the "this food puts you over" warnings, and the plan items can be set as reminders. |
| Mood music pick | `media/Music.kt` → `playForMe` | After a scan, Gemini gets your stress level, the music genres you chose at sign-up, and every song's title, artist and genre tags. It picks the first song and says why in one line. |
| Shop | `ui/screens/ShopScreen.kt` | Turns a description into 3–5 real products with typical prices. Requests that aren't for something you can buy are turned away. |
| Website food photos + chat | `server/index.js` → `/api/gemini` | The website calls Gemini through the server so the key never reaches the browser. |

Every call goes through `net/Gemini.kt`. If a model is overloaded or out of quota it moves to the next one: `gemini-3.8-flash` → 3.7 → 3.6 → 3.5 → `gemini-flash-latest`.

### ElevenLabs: one voice everywhere

During sign-up each person picks a voice. There are 16 accents, each with a woman's and a man's voice:

- American, British, Indian, Russian
- Spanish, French, German, Italian
- Nigerian, South African, Arabic, Chinese
- Japanese, Korean, Australian, Irish

Tapping a voice plays a short hello in it. **That one voice is used for everything SmartCam says, on the phone and on the website:**

- Gemini's chat answers (read automatically, or tap the speaker next to any answer)
- the fun facts read during a face scan and while Gemini looks at a food photo
- the spoken guidance in the Meditation game
- the website's spoken replies

Mood music gets quieter while the voice speaks. If ElevenLabs can't be reached, the phone's built-in voice takes over.

| Where | What |
|---|---|
| `android/.../net/Voice.kt` | The voice list, text-to-speech calls (`eleven_multilingual_v2`), playback, audio focus and the fallback voice. |
| `android/.../ui/screens/VoicePicker.kt` | Woman/Man switch, accent list and preview. Used in onboarding and **Settings → Voice**. |
| `android/.../ui/Facts.kt` | Fun facts that type themselves out while being read aloud. |
| `server/index.js` → `/api/tts` | Website speech. It looks up the signed-in person's chosen voice in Tiger Data, so the website sounds the same as their phone. |

### Tiger Data (TimescaleDB / Postgres): accounts and sync

Everything a member saves is kept on the phone and mirrored to Tiger Data, keyed by username. That's why they can sign in on another phone or on the website and find their data there. Syncing runs in the background; the app never shows connection screens.

| Table | Holds |
|---|---|
| `sc_users` | Accounts: username, PBKDF2 password hash, role. |
| `sc_state` | Each person's profile, settings (including their chosen voice), plan, alarms and so on, as JSON. |
| `sc_foods` | Food log, one row per item. The phone and website both read and write it. |
| `sc_vitals` | Every scan result, stored as time-series data. |

| Where | What |
|---|---|
| `android/.../data/Tiger.kt` | JDBC connection; creates the tables; upserts foods, vitals and state; manages accounts. |
| `android/.../data/Repo.kt`, `data/Accounts.kt` | Per-user local storage, pulling data down at sign-in, background pushes. |
| `server/index.js` | The same tables through `pg`, behind `/api/login`, `/api/signup`, `/api/foods`, `/api/vitals`, `/api/users`. |
| `db/schema.sql` | Table definitions. |

### Solana: paying in the shop

Checkout in the **Shop** tab uses **Solana Pay**:

1. SmartCam converts the item's USD price to SOL at the live rate (CoinGecko).
2. It builds a `solana:` transfer request: amount, label, message, and a memo with the order id.
3. Your wallet app (Phantom, Solflare, …) opens with the exact amount filled in.

On the website you can connect Phantom directly. Payments go to the merchant wallet set in `SOLANA_MERCHANT`, on devnet by default.

| Where | What |
|---|---|
| `android/.../net/Solana.kt` | Price lookup, Solana Pay URL, opening the wallet. |
| `android/.../ui/screens/ShopScreen.kt` | Checkout and order history. |
| `web/js/services.js` → `solana` | Phantom connection and Solana Pay on the website. |

### Other services and libraries

| What | Used for | Where |
|---|---|---|
| **Open Food Facts** | Nutrition and Nutri-Score for scanned barcodes (values per 100 g; you enter the grams you ate). | `net/FoodFacts.kt` |
| **ML Kit barcode scanning** | Live barcode scanning, scanning from a photo, or typing the number. | `food/BarcodeReader.kt`, `ui/screens/FoodScreen.kt` |
| **Android usage stats** | Screen time by app and day; sleep estimated from the longest stretch the screen stayed off each night. | `phone/Usage.kt` |
| **Device admin (lock only)** | Bedtime screen lock and daily app limits. | `phone/Guard.kt` |
| **Step sensor** | Steps on the Home widget and in the week chart. | `MainActivity.kt` |
| **AlarmManager / Clock app** | Plan reminders and alarms. | `phone/Reminders.kt`, `phone/PhoneKit.kt` |
| **MediaPlayer** | Mood music played inside SmartCam, from `music/` and songs people add in Settings. | `media/Music.kt` |
| **Jetpack Compose** | The whole UI: liquid-glass design, widgets that grow when tapped, swipe between tabs, haptics, first-run tour. | `ui/` |

---

## Using the app

- **Swipe left or right** to move between tabs (Home, Camera, Food, Phone, Shop), or tap the tab bar. The first and last tabs stretch against your finger and spring back.
- **Tap a widget** on Home or Phone to open it over the others. Tap outside it to close.
- **Ask Gemini** is at the bottom of every tab except Shop.
- **Settings** (⋮) has these sections:
  - account
  - permissions
  - mood music
  - voice
  - screen lock
  - haptics
  - theme

### Accounts

| Kind | What they get |
|---|---|
| **Member** | The full app. New members answer a few questions first: name, age, height and weight, activity, goals, diet, music taste, voice, sleep and step goal. They also get a short tour. |
| **Admin** | Everything, plus **Settings → Admin · accounts** to see and delete accounts. |
| **Guest** | Each tab shows an overview of what it does. Nothing is saved. |

Passwords are stored only as PBKDF2 hashes, on both the phone and the server.

---

## Privacy

SmartCam's privacy policy is in [PRIVACY.md](PRIVACY.md). The same policy is in the app (sign-in screen and **Settings → Privacy**) and on the website (`web/privacy.html`). Members can delete their account and all its data from **Settings → Privacy → Delete my account**.

---

## Setup

### Keys

All keys live in **one file, `server/.env`**. It's in `.gitignore`, so it's never committed.

| Key | Used by |
|---|---|
| `GEMINI_API_KEY`, `GEMINI_MODEL` | app + website |
| `ELEVENLABS_API_KEY`, `ELEVENLABS_VOICE_ID` (default voice) | app + website |
| `PRESAGE_API_KEY` | app |
| `DATABASE_URL` (Tiger Data connection string, with password) | app + website |
| `SOLANA_MERCHANT`, `SOLANA_NETWORK` | app + website |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD` | the admin account |
| `SESSION_SECRET` | signs website logins |

The Android build reads `server/.env` and compiles the keys into the APK, so the app works anywhere without a PC running. The downside is that anyone with the APK could extract the keys. **Don't post the APK publicly**, and rotate the keys before any public release. The website already keeps its keys on the server.

### Install on a phone

1. On the phone, turn on **Developer options → USB debugging**.
2. Plug it in and tap **Allow**.
3. Run `adb install -r SmartCam.apk`.

Or copy `SmartCam.apk` to the phone and open it.

### Mood music

Six smooth ambient tracks are included (two per stress level). To add more, put audio files in `music/high-stress`, `music/moderate-stress` or `music/low-stress`, then rebuild. Genre and artist tags help Gemini pick. See `music/README.md`. People can also add songs from their phone in **Settings → Mood music**.

### Build the app

Gradle must run on **Java 17**, because Kotlin 2.2 (which Presage requires) can't run on Java 25. In `%USERPROFILE%\.gradle\gradle.properties` add:

```
org.gradle.java.home=C:/Users/<you>/.jdks/jdk-17...
```

Then:

```sh
cd android
gradlew assembleDebug                 # → app/build/outputs/apk/debug/app-debug.apk
gradlew connectedDebugAndroidTest     # barcode tests on a connected phone/emulator
```

### Run the website

```sh
cd server
npm install    # first time
npm start      # → http://localhost:8787
```

---

## Status

**Working and tested** on the emulator and a Galaxy A54:

- sign-up, onboarding and the tour
- all widgets
- Gemini chat, food photos, plans and the app-only limits
- ElevenLabs voices and fun facts
- mood music
- barcode scanning (6 instrumented tests)
- screen lock
- swiping between tabs
- guest mode

**Needs setup before the demo:**

- **Tiger Data:** add the database password to `DATABASE_URL`. Until then, accounts stay on the phone that created them and the website can't sign people in.
