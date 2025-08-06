# Contributing to QRServ

If you have reached this document, you might be interested in contributing.

## Functionality

Before creating a new issue or putting together a pull request (regarding feature requests or potential improvements), please refer to the [design philosophy](PHILOSOPHY.md). It also serves as a Q&A.

## Language

At this time, translations are being accepted through GitHub.

-   Ensure that locale-specific formatting, such as decimal points, correspond to the respective language
-   Please do not translate into languages that you are not experienced in

When adding a new language:

-   Add a language file to [lib/l10n/](lib/l10n/) with the file name format of `app_{language_code}.arb` (ISO 639-1 -- two character language code) and based on [app_en.arb](lib/l10n/app_en.arb) -- ensure that `@@locale` within the file also has the respective language code
-   Add a new entry to the bottom of the list in [locales_config.xml](android/app/src/main/res/xml/locales_config.xml) within the `locale-config` tags -- this is used for the per-app language feature

## Changelog

When creating or updating a changelog for a new release:

-   Update [CHANGELOG.md](CHANGELOG.md)
-   Add a new file under [fastlane/metadata/android/en-US/changelogs/](fastlane/metadata/android/en-US/changelogs/) that has the file name format of `{version_code}.txt` where `{version_code}` is the number found after the `+` sign under the `version` key in [pubspec.yaml](pubspec.yaml) -- this is for IzzyOnDroid
    -   Please note that once a new release is published, the version of the fastlane changelog under that release cannot be updated
