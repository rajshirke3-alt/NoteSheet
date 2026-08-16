# NoteSheet

Native Android note-taking app for ward/bed records — Excel-style rows & columns, search,
PDF export / print, and hands-free voice entry that fills the right column no matter what
order you speak the fields in.

## What's new in this version

- **Voice dictation, manual stop only.** Tap "Start Recording" and keep talking — the app
  restarts the recognizer under the hood every time it pauses, so it never stops on its own.
  It only stops when you tap "Stop".
- **Order-independent field filling.** Say a marker word, then the words that follow go into
  that field, until you say another marker:
  - `"bed number"` / `"bed no"` / `"bed"` → Bed Number
  - `"patient name"` / `"patient"` / `"name"` → Patient Name
  - `"primary consultant"` / `"consultant"` / `"doctor"` → Primary Consultant
  - `"details"` / `"detail"` → Details
  - Say "patient name" first, then "bed number" later — both land in the right box.
  - Say "details" once and just keep talking — everything after that stays in Details until
    you say another marker.
- **Date is never affected by speech order.** The date/time stamp is taken from the system
  clock the moment you hit Save, not from anything you say.
- **Medical term auto-correction** (`MedicalDictionary.kt`) — corrects the terms you listed
  (OGD Scopy, Colonoscopy, MRCP, ERCP, EUS Guided, CECT/CT Abdomen, LAP, Hemorrhoidectomy,
  Cholecystectomy, Hepatitis A/B/C, NBM, NS, RL, etc.) whenever the recognizer mishears them,
  using exact-phrase matching plus a close-match fallback for single tricky words.
- **Doctor name auto-correction** (`DoctorDictionary.kt`) — normalizes references to
  Dr. Yatin Sagwekar, Dr. Chaitanya, Dr. Bharat, Dr. Poonam, Dr. Sonali Gautam and
  Dr. Dipak Bhangale to the exact spelling every time, with or without "Dr"/"doctor" spoken.
- Table view (Bed No / Patient Name / Primary Consultant / Details), tap a row to edit,
  long-press to delete, search box filters across all four fields.
- **PDF & Print** button — builds a table PDF of every record and either sends it straight
  to Android's Print dialog or lets you save/share the .pdf file.

Want to tweak the term lists later? They're plain Kotlin lists at the top of
`app/src/main/java/com/notesheet/app/MedicalDictionary.kt` and `DoctorDictionary.kt` —
add or edit entries there, no other code changes needed.

## Project structure

```
NoteSheet/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/notesheet/app/
│       │   ├── MainActivity.kt        (UI, voice control, search, save/delete)
│       │   ├── VoiceParser.kt         (order-independent field parsing)
│       │   ├── MedicalDictionary.kt   (clinical term correction)
│       │   ├── DoctorDictionary.kt    (consultant name correction)
│       │   ├── PatientRecord.kt / RecordDao.kt / AppDatabase.kt  (Room database)
│       │   ├── RecordAdapter.kt       (RecyclerView table rows)
│       │   └── PdfExporter.kt         (PDF build + Print framework)
│       └── res/                       (layouts, colors, strings)
├── .github/workflows/build-apk.yml    (GitHub Actions — builds the APK automatically)
├── build.gradle / settings.gradle / gradle.properties
└── README.md
```

## Getting the APK via GitHub Actions

### 1. Push this project to a GitHub repo

The workflow file lives in `.github/workflows/` — a **hidden** folder (anything starting
with a dot). This is what tripped things up last time: apps like Android Studio's Project
view or Windows/macOS file explorers hide dot-folders by default, so it *looks* like the
file never got added, but the folder is really there on disk.

To make sure it actually gets committed, always add everything with `-A` rather than
picking files by hand:

```bash
cd NoteSheet
git init                      # skip if the repo already exists
git add -A                    # -A (not just ".") explicitly includes dotfiles/dotfolders
git commit -m "NoteSheet with voice entry and medical term correction"
git branch -M main
git remote add origin https://github.com/<your-username>/<your-repo>.git
git push -u origin main
```

Double-check it made it up by browsing to
`https://github.com/<your-username>/<your-repo>/tree/main/.github/workflows` — you should
see `build-apk.yml` listed there.

### 2. Let Actions build it

As soon as you push to `main`, the **Build NoteSheet APK** workflow runs automatically
(you can also trigger it manually from the **Actions** tab → *Build NoteSheet APK* →
*Run workflow*).

The workflow:
- checks out the repo
- installs JDK 17 and Gradle (current, non-deprecated action versions:
  `actions/checkout@v4`, `actions/setup-java@v4`, `gradle/actions/setup-gradle@v3`,
  `actions/upload-artifact@v4` — this avoids the deprecation warnings you hit before)
- runs `gradle assembleDebug`
- uploads the resulting APK as a build artifact

### 3. Download the APK

Go to the **Actions** tab → click the latest successful run → scroll to **Artifacts** →
download **NoteSheet-debug-apk**. Unzip it and you'll have `app-debug.apk`, ready to
sideload onto your phone (enable "Install unknown apps" for whichever app you use to
open the file).

## Notes on the voice feature

- First use will prompt for the microphone permission — allow it.
- Recording keeps going indefinitely (the app silently restarts the recognizer each time
  it pauses) until you tap **Stop**.
- It works fully offline-capable on most devices, but Android's speech engine may briefly
  use data/network depending on the phone — no extra setup needed on your end.
- If a field already has text in it and you say that marker again, the new words are
  appended rather than overwriting what's there — so you can add to Details in more than
  one go during the same recording.
