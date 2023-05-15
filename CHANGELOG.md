# Changelog

## 2.1.3

Android:

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
