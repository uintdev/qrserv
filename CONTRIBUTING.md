# Contributing to QRServ

If you have reached this document, you might be interested in contributing.

## Functionality

Before creating a new issue or putting together a pull request (for feature requests or potential improvements), please refer to the [design philosophy](PHILOSOPHY.md). It also serves as a Q&A.

## Language

At this time, translations are being accepted through GitHub.

- Ensure that locale-specific formatting, such as decimal points, corresponds to the respective language.
- Please do not translate into languages in which you are not experienced.

When adding a new language:

- Add a language file to [lib/l10n/](lib/l10n/) with the file name format `app_{language_code}.arb` (ISO 639-1 -- two-character language code) and based on [app_en.arb](lib/l10n/app_en.arb). Ensure that `@@locale` within the file also has the respective language code.
- Add a new entry to the bottom of the list in [locales_config.xml](android/app/src/main/res/xml/locales_config.xml) within the `locale-config` tags -- this is used for the per-app language feature.

Addition and modification of translations during development:

- These are done with best effort and judgment.
    - This is done to provide something hopefully understandable instead of falling back to the default language or using a localization string as a placeholder.
        - The two things being avoided might still be fine to do, but in this project specifically, I felt this was the most suitable approach.
    - This is still very prone to errors (some much larger than others), so contributions that offer corrections are helpful.
- The limit of such effort applies to the application's language files.
- This **does not** extend to what is under [fastlane/metadata/android](fastlane/metadata/android/). In most cases, metadata not under [en-US](fastlane/metadata/android/en-US/) are not officially maintained, so information such as descriptions and screenshots can fall behind. Pull requests to add or update metadata are still welcome, though.
    - **Note:** The [fastlane](fastlane/) directory is currently used solely for IzzyOnDroid app listing metadata. Any changes made are expected to be reflected in that F-Droid repository once a new app release is created, picked up, and processed successfully.

## Changelog

When creating or updating a changelog for a new release:

- Update [CHANGELOG.md](CHANGELOG.md).
- Add a new file under [fastlane/metadata/android/en-US/changelogs/](fastlane/metadata/android/en-US/changelogs/) with the file name format `{version_code}.txt`, where `{version_code}` is the number found after the `+` sign under the `version` key in [pubspec.yaml](pubspec.yaml). This is for IzzyOnDroid.
    - Please note that once a new release is published, the version of the fastlane changelog under that release cannot be updated.
