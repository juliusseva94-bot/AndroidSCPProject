# SCP Archive for Android

**An offline-capable SCP Foundation encyclopedia for Android.** This project contains native Java Android source code and an automated GitHub Actions build for an installable debug APK. It is *not* an APK in this ZIP. The current environment did not have the Android SDK needed to compile or device-test a binary.

## What it does

- Lists all available English-language **SCP item** records from the [community-maintained SCP Data API](https://scp-data.tedivm.com/), which states that its crawler runs daily.
- Displays each entry's SCP number, familiar name when present in the SCP Wiki's official series index, classification tag when available, an **extractive description excerpt**, author/creator metadata, original wiki source link, and a wiki-hosted image when available.
- Search by number, name, class/tag, or downloaded excerpt. Mark favorites, see newly added items, and mark individual records saved.
- **Sync Latest** manually fetches all current metadata and appends new entries. It preserves favorites and previously downloaded summaries.
- **Offline Mode** switches off networking. All downloaded database records remain readable. Images are available offline if cached.
- **Download missing summaries** downloads series data and extracts each item's Description section for offline reading. A special-format entry may have no extractable synopsis.
- **Download all available pictures** caches image files on the device. This can use substantial storage and time. Some entries don't have an eligible image in the dataset.

## Get an APK without installing Android Studio

1. Extract this ZIP. Create a **new private or public GitHub repository**, then upload the unzipped project contents **including the `.github` directory** (not the parent project folder).
2. In the repository, choose **Actions → Build Android APK → Run workflow**. Enable Actions if GitHub requests it. The workflow fetches the complete current SCP index, descriptions, and names before compiling.
3. Open the completed workflow run. Under **Artifacts**, download `SCP-Archive-Android-APK`. Extract the artifact ZIP, copy `app-debug.apk` onto your phone, open the APK, and allow installation from that source if prompted.

GitHub Actions requires a network connection. It downloads Gradle and Android dependencies and calls the SCP Data API; if that upstream source is down, the full-build step will fail rather than silently pretending the five-entry starter catalog is complete. The workflow generates a **debug-installable APK**, not a Play Store release-signed APK. GitHub build artifacts have limited retention.

## Build locally in Android Studio

1. Install current Android Studio (with Android SDK 35 and JDK 17).
2. Optional but recommended: run `python tools/build_catalog.py --require-full` from the project directory **while online**. This replaces the five-entry starter catalog with the complete current archive and available synopsis text. This step can download significant data.
3. Open the `SCP_Archive_Android` directory in Android Studio, let Gradle sync, and select **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
4. Find `app/build/outputs/apk/debug/app-debug.apk` and install it on Android 8.0 or later.

Without running the catalog builder first, the project includes only **five starter entries** at first launch. Once online, tapping **Sync latest** obtains the full current index. Users can then separately download their descriptions and image assets.

## Offline options

**Metadata:** After initial sync or installing an APK built via the workflow, the item index (numbers, titles, and source URLs) is stored locally in SQLite.

**Synopsis:** The GitHub build attempts to package an extractive synopsis for each conventional article. Nonstandard articles may not have a Description section, and failed upstream series downloads mean some synopses may remain unavailable. The on-device summary download fills gaps where possible. An excerpt is not a human-written overview and may not capture the whole article.

**Images:** The APK deliberately does *not* bundle every SCP image. The user can cache all eligible wiki-hosted pictures from the Offline settings screen. Some pages have none, and some media may be unavailable or unsupported. Full offline image downloads can consume gigabytes; keep the app open and use Wi-Fi. There is a cancel button that stops at the next file boundary.

**Updates:** Updates are manual, not automatic background downloads. Re-enter online mode and tap **Sync latest** for new entries. Additional synopsis and picture downloads are separate.

## SCP content, copyright, and images

SCP Foundation concepts and derived excerpts are shared under [Creative Commons Attribution-ShareAlike 3.0](https://creativecommons.org/licenses/by-sa/3.0/). Attribution belongs to the SCP Wiki and the **individual article authors**. The app presents original page links and the creator metadata supplied by its source; individual images can have separate artist credits and licenses shown on each wiki article's image/license box. Do not assume an image can be redistributed just because its URL exists in the metadata. Do **not** redistribute a package of cached images without checking and retaining those individual credits. In particular, do not redistribute SCP-173's historically removed copyrighted photograph.

- SCP official wiki: https://scp-wiki.wikidot.com/
- SCP licensing guide: https://scp-wiki.wikidot.com/licensing-guide
- SCP image use policy: https://scp-wiki.wikidot.com/image-use-policy
- SCP data mirror: https://scp-data.tedivm.com/

This is an **unofficial fan reader**, not affiliated with or endorsed by SCP Wiki staff. Article-specific creator metadata may not fully reflect coauthors or later revisions; refer to the linked source articles for complete credits.

## Technical notes

- **Source:** Java 17, native Android views, Android SQLite, no third-party application runtime libraries.
- **Targets:** `minSdk 26` (Android 8.0+), Android SDK 35.
- **Data origin:** Metadata: `/data/scp/items/index.json`; article text: per-series `content_file`; canonical names: official series listings.
- **Privacy:** No analytics, login, or data collection. The app makes requests to the SCP Data API and SCP Wiki/media hosts when online, and opens original articles only by user action.
- **Local persistence:** SQLite database in the app's private storage, plus cached image files. Uninstalling the app removes stored data unless the operating system retains a backup.
- **Current status:** Source and build workflow created, script validated locally, **APK compilation and real-device behavior have not been verified** in this environment because the Android SDK/network access for the build was unavailable.

## Known limitations and future improvements

- Image attributions should ideally be extracted from the corresponding article's license box and stored per asset. For now the linked source is essential for verifying credits.
- The dataset covers English-language SCP item pages. It is not an exhaustive mirror of tales, hubs, and international branches.
- The `created_by` field is a creator identifier, not guaranteed to capture every current author.
- Offline full downloads currently run while the app is open, not under WorkManager; Android may stop them if the app is closed.
- An index update preserves old summaries, so select **Refresh all summaries** to force re-extraction after revisions.
- Cached images aren't automatically garbage-collected when source images change.
- Large series content downloads may require a device with ample memory; update in stages when possible.
