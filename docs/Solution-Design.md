# OmniFind: Solution Design

<!-- build:begin -->**OmniFind 0.3.0-beta.3 · Build 6** · 2026-10-20 · Android 9 (API 28) to Android 17 (API 37)<!-- build:end -->

Requirements are in [Product-Specification.md](Product-Specification.md); what changed per build is in
[Release-Notes.md](Release-Notes.md).

## 1. Architecture

```
Files you choose ──► FolderPolicy ──► Crawler ──► Readers (text, docx/odt, PDF, pictures)
                     (allowed?)                      │
                                                     ▼
                                   IndexStore (SQLite + FTS5, one file)
                                   files · segments (768 × float16 vectors) · keyword index
                                                     ▲
                      IndexWorker (WorkManager, foreground) ── Embedder (JNI → llama.cpp, EmbeddingGemma 2 Q8_0)
Your words ──► Searcher: query vector (text model) + FTS5 words ──► hybrid ranking ──► results
```

| Layer | Code | Notes |
|---|---|---|
| Engine | `app/src/main/cpp/embedder.cpp`, `engine/Embedder.kt` | Port of DigUp's LlamaEmbedder; llama.cpp b11461 + DigUp's no-logits patch; CPU, ARMv8.0 baseline |
| Model | `engine/ModelFiles.kt`, `engine/ModelDownloadWorker.kt` | Pinned revision + SHA-256; resumable download |
| Index | `index/IndexStore.kt`, `index/Indexer.kt`, `index/IndexWorker.kt` | Two passes: keywords, then vectors (first pieces first); separate text and picture fingerprints |
| Folders | `index/FolderPolicy.kt`, `index/Volumes.kt`, `index/IndexSettings.kt` | See section 3 |
| Search | `search/Searcher.kt`, `cpp/vectors.cpp` | DigUp's ranking: per-kind z-scores, gate 2.0, word coverage, kind hints; NEON scoring |
| UI | `ui/*`, `ui/theme/*` | Jetpack Compose, Sora, edge to edge, max content width 600 dp |
| Release | `assets/release/release.json`, `release/Release.kt`, `ui/TourScreen.kt` | See section 4 |

## 2. Data

`index.sqlite` (app-private storage): `files` (path, kind, size, mtime, state, info JSON), `segments` (kind,
modality, page/chunk, rank, excerpt, vector), `fts` (FTS5: names, folders, text). Schema 2. Vectors are 768 float16,
L2-normalised; meta records the vector source so a model or llama.cpp change re-embeds, and the picture settings
separately so changing photo detail never re-embeds text.

## 3. Folder access and security (FR-07 to FR-09, NFR-02)

Following Android's storage security guidance:

- **Permissions by version.** Android 9–10: `READ_EXTERNAL_STORAGE` (declared up to API 32). Android 11+: "All files
  access" (`MANAGE_EXTERNAL_STORAGE`), granted by the person in Settings. Nothing is ever written to shared storage.
- **One policy, applied three times** (`FolderPolicy`): when listing folders to choose (`IndexSettings.choices`), when
  reading the saved choices (`IndexSettings.roots` → `sanitize`, so a tampered or now-protected path is dropped), and
  while walking (`mayEnter`, `mayRead`).
- **Protected:** `Android/` (other apps' `data`, `obb`, `media`), `LOST.DIR`, `.android_secure`, trash and PC system
  folders, anything hidden (starting with "."), anything not on a storage volume (`/data`, `/system`).
- **No escaping:** paths are compared by their canonical (real) location, so `Download/../Android/data` and a symbolic
  link from Download to elsewhere are refused; the walk never follows links and reads regular files only.
- **Refresh (FR-08):** `IndexViewModel.refreshVolume` lists one volume's top-level folders again from the disk on a
  background thread, through the same policy, and marks the ones not listed before as New. Folders that vanished drop
  out; chosen folders that are missing (a card taken out) keep their files in the index.
- **Sharing out:** results open through a non-exported `FileProvider` granting read access to one file at a time.
- **Backups:** `allowBackup=false` and data-extraction rules exclude the index and settings from cloud backup and
  device transfer.

## 4. Release manifest and the tour (FR-12, FR-13)

```
release.json ──► Gradle (versionName, versionCode = build number)
      │
      ├─► app: Release.load() ──► TourGate (seen build < this build?) ──► TourScreen (pager, numbered steps)
      │
      └─► tools/release/make_docs.py ──► Release-Notes.md, walk-through in this document and the PRD
                    ▲
TourScreenshotTest, DeviceMatrixTest (build validation) ──► screenshots ──► assets/release/tour/*.webp
```

- **One source:** `app/src/main/assets/release/release.json` holds version, build number, date, test cases, the
  numbered tour (title, overview, status, screenshot, test cases), fixes and known issues. Gradle reads the version
  from it, so the APK, the tour and the documents can't carry different build numbers.
- **Numbering:** steps are numbered 1…n in the order someone meets the features (search, choose types, choose
  folders, get the model, every screen, the tour itself); each names the test cases that verify it.
- **When it shows:** `TourGate` keeps the last build whose tour was seen; a fresh install (0) or an update (lower) shows
  it once. Engine → What's new opens it again.
- **Screenshots** are rendered by Robolectric/Roborazzi at a 412 × 892 dp phone (2×) with representative data, then
  stored as WebP in the app's assets (density-independent, about 30–60 KB each) and as PNG in `docs/release/<version>/`.
- **Checks:** `ReleaseTest` (TC-09) fails the build if steps aren't numbered 1…n, name unknown test cases or lack their
  screenshot.

## 5. Compatibility (NFR-03, NFR-04)

| Area | Design |
|---|---|
| Android 15+ edge to edge | `enableEdgeToEdge`; content inset by `safeDrawing`; tab bar under the navigation bar; keyboard insets |
| Densities | dp/sp only; vector icons; thumbnails sized in px from dp; launcher: adaptive + monochrome icon, PNG per bucket (36–192 px) |
| Sizes and text | Minimum (not fixed) heights; labels shrink to 70% then wrap; content max 600 dp, centred |
| 16 KB pages | NDK r28 links with 16 KB alignment; verified per library by `verify_apk.sh` (TC-10) |
| Predictive back | `enableOnBackInvokedCallback`, Compose `BackHandler` |

## 6. Build and validation

| Step | Command |
|---|---|
| Unit and screenshot tests | `./gradlew testDebugUnitTest` (TC-06 to TC-09) |
| Tour and documents | `python3 tools/release/make_docs.py` |
| Beta APK | `./gradlew betaApk` → `dist/OmniFind.apk` |
| APK checks | `tools/release/verify_apk.sh` (TC-10) |
| On a device | `adb shell am instrument -w -r com.omnifind.app.test/androidx.test.runner.AndroidJUnitRunner` (TC-01 to TC-05) |

## 7. Walk-through of this release

<!-- walkthrough:begin -->
### Feature walk-through: OmniFind 0.3.0-beta.3, build 6

Shown in the app as **Take a tour of the features** after installing or updating to build 6 (and from Engine → What's new). Screenshots and highlights come from build validation: a run on a device with sample data where noted, otherwise `TourScreenshotTest` and `DeviceMatrixTest`.

| Validation run | |
|---|---|
| Device | Pixel 8 Pro emulator (1344 × 2992, 480 dpi), Android 17 (API 37), arm64 |
| Data | 19 sample files from tools/demo/make_sample_data.py: 7 documents, 4 PDFs, 5 photos, 3 screenshots |
| Model | Copied from the Mac with adb (not downloaded in the app) |

**1. Search by what's in your files** · _Feature_

Describe what you're looking for in your own words. Exact words, names and codes are found at once; meaning fills in a moment later, across languages.

Highlights from the validation run:

- “can I keep a cat in the flat” → Lease 2026.pdf, page 3 (Pets and noise)
- “INV-2026-0417” → Invoice INV-2026-0417.pdf, by its exact code
- “hilsa fish recipe” (English) → the Bengali note ইলিশ রান্না.txt, first

Verified by: TC-01 (Engine matches DigUp's reference vectors), TC-02 (A question in one language finds a note in another), TC-03 (Words first, then meaning, on 30 notes)

![Step 1: Search by what's in your files](release/0.3.0-beta.3/tour/1-search-results.png)

_Screenshot: Pixel 8 Pro emulator, Android 17, build 5, sample data._

**2. Photos and screenshots by what they show** · _Feature_

Pictures are searched by what's in them, not their names. The Pictures filter shows matches as a grid.

Highlights from the validation run:

- “mountains by a lake” → mountain_lake.jpg (Pictures filter)
- “dog running on the beach at sunset” → beach_sunset.jpg
- “screenshot of a payment error” → the Payment declined screenshot

Verified by: TC-04 (Photos and screenshots found among notes)

![Step 2: Photos and screenshots by what they show](release/0.3.0-beta.3/tour/2-pictures.png)

_Screenshot: Pixel 8 Pro emulator, Android 17, build 5, sample data._

**3. Choose what to index** · _Feature_

Tick documents, PDFs and pictures. Files already indexed and unchanged are skipped, and nothing is embedded twice.

Highlights from the validation run:

- 19 files read; all 21 pieces (13 text, 8 pictures) searchable by meaning
- The next run: 0 new or changed · 19 skipped · 21 embedded, nothing read twice
- Before it, a caution: “7 documents, 4 PDFs, 8 pictures” are already in the index

Verified by: TC-04 (Photos and screenshots found among notes), TC-05 (Indexed files are skipped; unticked types stay)

![Step 3: Choose what to index](release/0.3.0-beta.3/tour/3-index-types.png)

_Screenshot: Pixel 8 Pro emulator, Android 17, build 5, sample data._

**4. Refresh folders, system folders protected** · _New in this build_

Tap Refresh under Phone storage or SD card to list folders made since; new ones are marked New. Android's system folders and other apps' files are never shown or read.

Highlights from the validation run:

- A folder made after opening the app: Refresh says “1 new folder: Scans”, marked New
- LOST.DIR, a hidden folder and Android/ exist on the device and are never listed
- A folder removed meanwhile drops out of the list

Verified by: TC-06 (Folder policy: system, hidden and linked folders refused)

![Step 4: Refresh folders, system folders protected](release/0.3.0-beta.3/tour/4-index-folders.png)

_Screenshot: Pixel 8 Pro emulator, Android 17, build 5, sample data._

**5. Download the model in the app** · _Feature_

Search by meaning needs a one-time 865 MB download over Wi-Fi. It resumes if interrupted and is checked before use; nothing about your files is sent.

Highlights from the validation run:

- Waits for Wi-Fi, shows progress, and stops on request
- Each file's SHA-256 is checked before it's used

Verified by: TC-10 (Release APK: signature, Android 9–17, 16 KB pages, icons)

![Step 5: Download the model in the app](release/0.3.0-beta.3/tour/5-engine-download.png)

_Screenshot: Rendered by TourScreenshotTest (the download wasn't run on the emulator)._

**6. Fits every screen** · _Fixed in beta.2_

Phones from small ldpi screens to the Pixel 8 Pro, tablets and landscape, with large text: nothing overlaps or is cut off, and the app runs edge to edge on Android 15 to 17.

Highlights from the validation run:

- Checked from ldpi to xxxhdpi, on a tablet, in landscape and at 200% text
- Edge to edge on the Pixel 8 Pro emulator, Android 17

Verified by: TC-07 (Screens match the design mockups), TC-08 (Screens on ldpi to xxxhdpi, tablet, landscape, 130% and 200% text)

![Step 6: Fits every screen](release/0.3.0-beta.3/tour/6-screens.png)

_Screenshot: Rendered by DeviceMatrixTest._

**7. This tour, after every update** · _New in this build_

The tour opens once after you install or update OmniFind. Open it again any time from Engine → What's new.

Highlights from the validation run:

- Shown after an update (build 5 → 6 on the emulator); not again on relaunch
- Seven numbered steps, then the fixes and known issues

Verified by: TC-09 (Tour shows after install or update, once per build)

![Step 7: This tour, after every update](release/0.3.0-beta.3/tour/7-tour.png)

_Screenshot: Rendered by TourScreenshotTest._
<!-- walkthrough:end -->
