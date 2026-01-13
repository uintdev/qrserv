import 'package:flutter/material.dart';
import '../l10n/generated/app_localizations.dart';
import 'package:flutter/foundation.dart';
import 'package:oktoast/oktoast.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:package_info_plus/package_info_plus.dart';
import '../theme.dart';
import '../components/filemanager.dart';

class About {
  // URL launch management
  void _launchURL(Uri url, BuildContext context) async {
    if (await canLaunchUrl(url)) {
      await launchUrl(url, mode: .externalApplication);
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
          packageInfo = AppLocalizations.of(context)!.about_packageinfofail;
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
  Future aboutDialog(BuildContext context) async {
    Widget packageInfo = await packageInfoRequest(context);

    await showDialog(
      context: context,
      builder: (contextDialog) => Dialog(
        child: Padding(
          padding: .fromLTRB(30, 20, 30, 10),
          child: Container(
            child: aboutDialogContents(context, contextDialog, packageInfo),
          ),
        ),
      ),
    );
  }

  Column aboutDialogContents(
    BuildContext context,
    BuildContext contextDialog,
    Widget packageInfo,
  ) {
    return Column(
      mainAxisSize: .min,
      children: [
        Align(
          alignment: .center,
          child: Container(
            child: Text(
              AppLocalizations.of(context)!.about_title,
              style: const TextStyle(
                fontSize: 25,
                fontVariations: [FontVariation('wght', 500)],
              ),
            ),
          ),
        ),
        const SizedBox(height: 5),
        Align(
          alignment: .center,
          child: Container(
            child: Column(
              mainAxisAlignment: .center,
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
          mainAxisAlignment: .end,
          children: [
            listButton(
              context,
              Icons.archive_rounded,
              'github.com',
              'uintdev/qrserv/releases',
              'Releases',
              ListPositionType.Front,
            ),
            const SizedBox(height: 5),
            listButton(
              context,
              Icons.code,
              'github.com',
              'uintdev/qrserv',
              AppLocalizations.of(context)!.about_opensource_title,
              ListPositionType.Between,
            ),
            const SizedBox(height: 5),
            listButton(
              context,
              Icons.local_cafe,
              'ko-fi.com',
              'uintdev',
              AppLocalizations.of(context)!.about_donate_title,
              ListPositionType.End,
            ),
            const SizedBox(height: 10),
            TextButton(
              onPressed: () {
                Navigator.pop(contextDialog);
              },
              style: TextButton.styleFrom(
                foregroundColor: Theme.of(context).primaryColor,
              ),
              child: Text(
                AppLocalizations.of(context)!.about_close,
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
      width: .infinity,
      child: ElevatedButton(
        onPressed: () {
          _launchURL(Uri(scheme: 'https', host: host, path: path), context);
        },
        style: ElevatedButton.styleFrom(
          foregroundColor: Theme.of(context).primaryColor,
          padding: .fromLTRB(6, 16, 6, 16),
          shape: RoundedRectangleBorder(
            borderRadius: listRadiusPosition(positionType),
          ),
        ),
        child: Stack(
          alignment: .center,
          children: [
            Align(
              alignment: .centerLeft,
              child: Padding(
                padding: .fromLTRB(24, 0, 0, 0),
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
    case .Front:
      result = .only(
        topLeft: .circular(16),
        topRight: .circular(16),
        bottomLeft: .circular(6),
        bottomRight: .circular(6),
      );
      break;

    case .Between:
      result = .only(
        topLeft: .circular(6),
        topRight: .circular(6),
        bottomLeft: .circular(6),
        bottomRight: .circular(6),
      );
      break;

    case .End:
      result = .only(
        topLeft: .circular(6),
        topRight: .circular(6),
        bottomLeft: .circular(16),
        bottomRight: .circular(16),
      );
      break;
  }

  return result;
}
