import 'package:flutter/material.dart';
import '../l10n/generated/app_localizations.dart';
import 'package:flutter/foundation.dart';
import 'package:oktoast/oktoast.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:package_info_plus/package_info_plus.dart';
import '../theme.dart';
import '../components/filemanager.dart';

class Info {
  // URL launch management
  void _launchURL(Uri url, BuildContext context) async {
    if (await canLaunchUrl(url)) {
      await launchUrl(url, mode: LaunchMode.externalApplication);
    } else {
      showToast(
        AppLocalizations.of(context)!.info_exception_linkopenfailed +
            url.toString(),
      );
    }
  }

  // Panel interface
  Future packageInfoRequest(BuildContext context) async {
    return FutureBuilder<PackageInfo>(
      future: PackageInfo.fromPlatform(),
      builder: (contextPackage, AsyncSnapshot<PackageInfo> snapshot) {
        String packageInfo = '';
        String appName = '';
        String version = '';
        String buildNumber = '';

        if (snapshot.hasError) {
          packageInfo = AppLocalizations.of(context)!.info_packageinfofail;
        } else if (snapshot.hasData) {
          appName = snapshot.data?.appName ?? '(null)';
          version = snapshot.data?.version ?? '(null)';
          buildNumber = snapshot.data?.buildNumber ?? '(null)';
          packageInfo = '$appName v$version (build $buildNumber)';
        } else {
          packageInfo = AppLocalizations.of(context)!.info_pending_appinfo;
        }

        return Text(
          packageInfo,
          style: const TextStyle(
            fontSize: 13,
            fontVariations: [FontVariation('wght', 400)],
          ),
        );
      },
    );
  }

  // Panel interface
  Future infoDialog(BuildContext context) async {
    Widget packageInfo = await packageInfoRequest(context);

    showDialog(
      context: context,
      builder: (contextDialog) => Dialog(
        child: Padding(
          padding: EdgeInsets.fromLTRB(30, 20, 30, 10),
          child: Container(
            child: infoDialogContents(context, packageInfo, contextDialog),
          ),
        ),
      ),
    );
  }

  Column infoDialogContents(
    BuildContext context,
    Widget packageInfo,
    BuildContext contextDialog,
  ) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Align(
          alignment: Alignment.center,
          child: Container(
            child: Text(
              AppLocalizations.of(context)!.info_title,
              style: const TextStyle(
                fontSize: 25,
                fontVariations: [FontVariation('wght', 500)],
              ),
            ),
          ),
        ),
        const SizedBox(height: 5),
        Align(
          alignment: Alignment.center,
          child: Container(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                packageInfo,
                Text(
                  (kReleaseMode ? 'Release' : 'Debug') +
                      ', ' +
                      (FileManager().isPlayStoreFriendly
                          ? 'Play Store'
                          : 'GitHub'),
                  style: const TextStyle(
                    fontSize: 13,
                    fontVariations: [FontVariation('wght', 300)],
                  ),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 20),
        Column(
          mainAxisAlignment: MainAxisAlignment.end,
          children: [
            listButton(
              context,
              Icons.code,
              'github.com',
              'uintdev/qrserv',
              AppLocalizations.of(context)!.info_opensource_title,
              ListPositionType.Front,
            ),
            const SizedBox(height: 5),
            listButton(
              context,
              Icons.archive_rounded,
              'github.com',
              'uintdev/qrserv/releases',
              'Releases',
              ListPositionType.Between,
            ),
            const SizedBox(height: 5),
            listButton(
              context,
              Icons.local_cafe,
              'ko-fi.com',
              'uintdev',
              AppLocalizations.of(context)!.info_donate_title,
              ListPositionType.End,
            ),
            const SizedBox(height: 10),
            TextButton(
              onPressed: () {
                Navigator.pop(contextDialog);
              },
              child: Text(
                AppLocalizations.of(context)!.info_close,
                style: TextStyle(
                  fontFamily: QRSTheme.fontFamily,
                  fontSize: 12.5,
                  fontVariations: const [FontVariation('wght', 500)],
                ),
              ),
            ),
          ],
        ),
      ],
    );
  }

  SizedBox listButton(
    BuildContext context,
    IconData icon,
    String host,
    String path,
    String label,
    ListPositionType positionType,
  ) {
    return SizedBox(
      width: double.infinity,
      child: ElevatedButton(
        onPressed: () {
          _launchURL(Uri(scheme: 'https', host: host, path: path), context);
        },
        style: ElevatedButton.styleFrom(
          padding: EdgeInsets.fromLTRB(6, 16, 6, 16),
          shape: RoundedRectangleBorder(
            borderRadius: listRadiusPosition(positionType),
          ),
        ),
        child: Stack(
          alignment: Alignment.center,
          children: [
            Align(
              alignment: Alignment.centerLeft,
              child: Padding(
                padding: EdgeInsets.fromLTRB(24, 0, 0, 0),
                child: Icon(icon),
              ),
            ),
            Center(
              child: Text(
                label,
                style: TextStyle(
                  fontFamily: QRSTheme.fontFamily,
                  fontSize: 13,
                  fontVariations: const [FontVariation('wght', 500)],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

enum ListPositionType { Front, Between, End }

BorderRadius listRadiusPosition(ListPositionType listPosition) {
  final BorderRadius result;

  switch (listPosition) {
    case ListPositionType.Front:
      result = BorderRadius.only(
        topLeft: Radius.circular(16),
        topRight: Radius.circular(16),
        bottomLeft: Radius.circular(6),
        bottomRight: Radius.circular(6),
      );
      break;

    case ListPositionType.Between:
      result = BorderRadius.only(
        topLeft: Radius.circular(6),
        topRight: Radius.circular(6),
        bottomLeft: Radius.circular(6),
        bottomRight: Radius.circular(6),
      );
      break;

    case ListPositionType.End:
      result = BorderRadius.only(
        topLeft: Radius.circular(6),
        topRight: Radius.circular(6),
        bottomLeft: Radius.circular(16),
        bottomRight: Radius.circular(16),
      );
      break;
  }

  return result;
}
