# Burrow — a file manager for Android

A fast, ad-free file manager with no tracking. Burrow has no internet permission, so your files never leave your phone.

## Features

- **Home**:
  - How much space is used and free on internal storage and any SD card.
  - Shortcuts to Downloads, Camera, Pictures, Documents, Music and Movies.
  - Categories (Images, Videos, Audio, Documents, Archives, APKs) with counts and sizes.
  - Recently changed files.
- **Browse**:
  - Folders in a list or grid, with thumbnails for photos, videos, album art and app icons.
  - A clickable path bar at the top, to jump back up to any parent folder.
  - Sort by name, date, size or type, with options for reverse order, folders first and showing hidden files.
- **Organise**: long-press to select files, then:
  - Copy or move them, then open another folder and tap **Paste here**.
  - Delete, rename, share or "Open with…".
  - See details, including a folder's total size.
  - Compress to a zip.
- **Zip files**: tap a zip to extract it into a new folder, or to open it in another app.
- **Search**: by name, either everywhere or only inside the current folder. Results appear as they're found.
- **Clean up**: review these lists and delete what you don't need. Nothing is deleted until you confirm.
  - **Large files** over 50 MB.
  - **Duplicate files**, matched by exact contents. **Select extras** keeps the oldest copy and selects the rest.
  - **Old downloads** you haven't touched in 3 months.
  - **APK installers** left behind after installing apps.
  - **Empty folders**.
- **Progress bar** with a cancel button for long copies, moves, zips and extractions.
- After any change, Burrow tells Android's media library, so gallery apps such as Glimpse update straight away.

## Permissions

The first time you open Burrow, it asks for **All files access**. On the next screen, turn on *Allow access to manage all files*. Android requires this permission for any app that browses every folder.

## Build and install

The same as Glimpse:

- **GitHub**: push this folder to a GitHub repository. The workflow builds `Burrow.apk` and publishes it on the repository's **Releases** page.
- **Android Studio**: open the folder, then press **Run**, or choose **Build → Build APK(s)**.

Requires Android 11 or later.
