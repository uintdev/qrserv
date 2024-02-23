# Changelog

## 2.3.2

-   Added Persian translation
-   Added enforcement of LTR text direction where RTL visually breaks text

## 2.3.1

-   Limited file tamper check to shared internal storage due to modification misreporting with application cache
    -   Bug fixed: selection with Direct Access Mode off and putting the application in the background can result in the file system watcher to misreport the file as modified

## 2.3.0

-   Added file tamper check (uses existing file removal detection method)
    -   This is an additional security measure, now that direct access to shared internal storage is possible
-   Press and holding QR code shows full URL in a tooltip
-   Moved file import button to the middle (for improved ergonomics on mobile)
-   File name tooltip now shows the full file path if file selection was done while in Direct Access Mode

## 2.2.0

-   Added support for direct internal storage access (press on the SD card icon to toggle) -- ideal for large files
    -   Direct access mode does not support multi-file selection
    -   For this to function under Android 13 or later, a new permission 'MANAGE_EXTERNAL_STORAGE' was added
    -   This grants access to '/storage/emulated/0'
-   Fixed an issue where there would be multiple instances of the file system watcher
-   File system watcher is now focused on the specific selected file rather than the directory it is under

## 2.1.7

-   Long IPv6 addresses now visually limited to 2 lines (font size lowers when close to hard limit -- begins truncating when the limit is reached)
-   Visually limited file name to one line (truncates if too long -- press and hold to display full file name in tooltip)
-   Spaced out the file information table further
-   Incremented compile and target SDK versions to 34 (Android 14)
-   Removed GMS (Google Mobile Services) dependency from Gradle build config
-   Use mavenCentral over jcenter
-   Increased Kotlin version

## 2.1.6

-   Fixed share button theming
-   Reworked imported UI card structure (to keep widget sizes consistent)

## 2.1.5

-   Adjusted 'about' dialogue box UI
-   Fixes and workarounds relating to recent releases of Flutter
    -   Increased Kotlin version -- there will be warnings from (abandoned) dependencies relying on the older version but nothing that would prevent building
    -   Reconstructed share button due to recent ElevatedButton defects regarding child widget alignment

## 2.1.4

Android:

-   Removed in-app immediate update check (Google Play Store) -- oops, best to keep it purely FOSS

## 2.1.3

All:

-   Improved Portuguese translation

## 2.1.2

Android:

-   Added in-app immediate update check (Google Play Store)

## 2.1.1

All:

-   Removed zone index from IPv6

## 2.1.0

Android:

-   Potential bug fix on certain devices regarding system UI colour changing animation (status and navigation bar -- by not animating them)
-   Unrestricted rotation for tablets
    -   Note: this does not mean the UI is optimised for large displays -- this is just for convenience
-   Added Turkish translation

All:

-   Updated UI
    -   New light theme
    -   Updated dark theme
    -   Changes depending on system theme

## 2.0.0

Android:

-   Multi-file selection support
    -   In-app and via sharesheet (i.e. selecting multiple images)
    -   Will be made into a ZIP archive file
    -   Tooltip when press and holding on the resulting archive file name will reveal the originally selected files
-   Themed icon support
-   Target Android 13 (SDK 33)
-   Added Russian translation
-   Fixed a crash that may occur when attempting to import video files via sharesheet

All:

-   Updated UI
    -   Improved appearance of dropdown
    -   Replaced slide panel with dialogue box
-   Reduced file selection FAB animation duration by 50ms
-   Added network check during import process
-   Improved state management
-   General reliability improvements

## 1.4.1

All:

-   Added language fallback (now falls back to English rather than German)

## 1.4.0

All:

-   Added Italian translation

## 1.3.2

All:

-   Reverted migration

## 1.3.1

All:

-   When a URL fails to open, it is now displayed in the presented error message
-   Migrated to official file picker package with merged custom-made patches

## 1.3.0

All:

-   Added port number in the file information section

## 1.2.0

Android:

-   Added the option to import a file via the sharesheet
-   Improved start up times

## 1.1.7

All:

-   Fixed an issue where the HTTP server can shutdown right before the download is done resulting in a download interruption (corrected the content length)
-   When the server gets shutdown by means that do not involve the shutdown button, the app state is now properly reset

## 1.1.6

Android:

-   Fixed an issue where URLs cannot be launched on Android 11 and later

## 1.1.5

All:

-   Removed debugging information from non-fallback file selection toasts

## 1.1.4

All:

-   Added additional user-friendly messages for certain file selection cases

## 1.1.3

Android:

-   Increased minimum API/SDK version requirement to 21 (Lollipop)

## 1.1.2

Android:

-   Reduced minimum API/SDK version requirement to 16 (Jelly Bean)

## 1.1.1

Linux:

-   Fixed minimum window size bug

All:

-   Increased spacing between card title and subtitle

## 1.1.0

All:

-   Added 'include filename in path' toggle

## 1.0.2

All:

-   Improved UI performance

## 1.0.1

Desktop:

-   Reduced minimum window height

All:

-   Fixed a typo in a translation

## 1.0.0

-   Initial release.
