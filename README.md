# QRServ

![Banner with app icon, app name 'QRServ' followed by 'transfer files with ease'](docs/banner/banner.png)
<br>

<p align="center">
    Transfer files with ease over a network.
    <br>
    <br>
    <a href="../../releases/latest" title="Latest release"><img src="https://img.shields.io/github/v/release/uintdev/qrserv" alt="Version"></a>
    &nbsp;&nbsp;
    <a href="LICENSE" title="License"><img src="https://img.shields.io/github/license/uintdev/qrserv" alt="License"></a>
    <br>
    <br>
    <a href="https://play.google.com/store/apps/details?id=dev.uint.qrserv"><img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="80"></a>
    &nbsp;&nbsp;&nbsp;&nbsp;
    <a href="https://ko-fi.com/uintdev" title="ko-fi"><img src="https://ko-fi.com/img/githubbutton_sm.svg" alt="Donate" height="80" width="300"></a>
</p>
<br>
<details>
    <summary>Screenshots</summary>
    <br>
    <p align="center">
        <img src="docs/screenshots/1.png" alt="Screenshot of app on the main screen" height="380">
        &nbsp;&nbsp;
        <img src="docs/screenshots/2.png" alt="Screenshot of app after selecting a file" height="380">
        &nbsp;&nbsp;
        <img src="docs/screenshots/3.png" alt="Screenshot of app after opening IP address list" height="380">
        &nbsp;&nbsp;
        <img src="docs/screenshots/4.png" alt="Screenshot of app when press and holding or hovering over file name -- tool tip is shown with full file name" height="380">
        &nbsp;&nbsp;
        <img src="docs/screenshots/5.png" alt="Screenshot of app when press and holding or hovering over file name -- tool tip is shown with original file names sizes of those included in the resulting file archive" height="380">
        &nbsp;&nbsp;
        <img src="docs/screenshots/6.png" alt="Screenshot of app showing that a 10 Gigabit file was selected -- this shows the ability to work with large files" height="380">
    </p>
</details>
<br>

## About

QRServ is a file sharing application that utilises its own HTTP server to serve files while having a clean & functional user interface.

## Features

-   QR Code
    -   Tap on the QR code to show full URL in a tooltip
    -   Press and hold on the QR code to copy full URL to clipboard
-   Share / Copy to clipboard option
-   Multi-file selection support (Android only)
    -   Multi-file selection would result in a ZIP archive
        -   Tooltip when press and holding on the resulting archive file name will reveal the originally selected files
-   Direct Access Mode (Android only)
    -   Only available on Android 10 or earlier on the Play Store version
    -   To use this feature on Android 11 or later, use the GitHub version (link is in-app under the 'about' dialog) -- please note that the Play Store version needs to be uninstalled first as it would be signed using a different certificate
    -   Large files? Use direct access mode to use direct access to internal storage as to avoid attempting to copy the selection into app cache
    -   The file manager for this mode only supports single file selection
    -   The mode can be toggled by pressing on the SD card icon
-   File selection removal and modification detection (latter only available with DAM)
-   Import via sharesheet (Android only)
-   Show and hide filename in download URL path
    -   Long press the share button to toggle
-   Notify when a client requested the hosted file and when that download finishes (includes IP address of requestor)
-   Various IP addresses from different network interfaces can be chosen
-   HTTP server uses an unused ("random") port
-   Supports Android, Windows and Linux platforms
-   Supports various languages:
    -   English
    -   French (Français)
    -   German (Deutsch)
    -   Hungarian (Magyar)
    -   Italian (Italiano)
    -   Polish (Polski)
    -   Portuguese (Português)
    -   Spanish (Español)
    -   Russian (Русский)
    -   Turkish (Türkçe)
    -   Persian (فارسی)

## Releases

Android builds can be found in the [releases](../../releases) section of this repository.

Note: Android builds on GitHub will have a different certificate than builds on the Play Store. In other words, you cannot upgrade a build from installation source A via source B and vice versa.

### Play Store and GitHub version differences

As you may be aware, there are two different Android builds of this application. This section will cover the differences.

#### Play Store

-   Direct Access Mode is **not** available for Android 11 or later due to the `MANAGE_EXTERNAL_STORAGE` runtime permission requirement (see issue #20)
    -   In short, Google Play had became far more strict about the usage of such sensitive permission since June 2024
    -   There hasn't been much luck in terms of using the MediaStore API.. at least from existing Flutter packages that _kind of_ act like a wrapper (typically, there's issues and limitations) -- it would likely be the case of writing that functionality from scratch using Kotlin

#### GitHub

-   Direct Access Mode **is** available for all supported Android versions, as `MANAGE_EXTERNAL_STORAGE` can be used to allow support for Android 11 or later

#### Changing build types

By default, the source code builds to the GitHub version. The build type can be changed through the [filemanager.dart](lib/filemanager.dart) file in the `isPlayStoreFriendly` section (instructions can also be found there). Update the permissions under [AndroidManifest.xml](android/app/src/main/AndroidManifest.xml) as appropriate.

### Desktop

Last desktop builds (Windows, Linux) can be found in the [releases section under v1.1.1](../../releases/tag/v1.1.1).

## Contributing

Before creating a new issue or putting together a pull request (particularly regarding feature requests or potential improvements), please refer to the [design philosophy](PHILOSOPHY.md). It also serves as a Q&A.

## Building

### Android

If you wish to have debugging symbols for an app bundle release, ensure you have the Android NDK installed. You may need to specify the `ndk.dir` in the `local.properties` file.
<br>
However, if you do not plan to do a Play Store release, you may remove the `ndk` block from `android.defaultConfig` in the gradle build file.

### Windows

Windows builds normally require `Visual C++ Redistributable for Visual Studio 2015` to run. There are two ways you could go about it:

1. Install [Visual C++ Redistributable for Visual Studio 2015](https://www.microsoft.com/en-us/download/details.aspx?id=48145)
2. Bundle the required files in the root directory of the compiled executable (`msvcp140.dll`, `vcruntime140.dll`, `vcruntime140_1.dll`) -- ideal when distributing

You could [package builds as a MSIX](https://pub.dev/packages/msix) but that is only practical if you plan to get or already have a code signing certificate.

## Licencing

Google Play and the Google Play logo are trademarks of Google LLC.

Poppins (the font) is licenced under [OFL-1.1](fonts/OFL.txt).

QRServ is licenced under the [MIT license](LICENSE).

## Translations and translators

New and existing translations are very welcome. Via issue, pull request, or even email. Credit will be given unless opted out.

Thanks to the following users for helping out with language translation:

| User                                         | Language(s) |
| -------------------------------------------- | ----------- |
| [miklosakos](https://github.com/miklosakos)  | Hungarian   |
| [MrRocketFX](https://twitter.com/MrRocketFX) | Polish      |
| [utf-4096](https://github.com/utf-4096)      | French      |
| [SimoneG97](https://github.com/SimoneG97)    | Italian     |
| [guidov2006](https://github.com/guidov2006)  | Spanish     |
| [solelychloe](https://arciniega.one)         | Russian     |
| [metezd](https://github.com/metezd)          | Turkish     |
| princessmortix                               | Portuguese  |
| [alr86](https://github.com/alr86)            | Persian     |
