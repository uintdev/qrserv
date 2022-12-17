import 'package:flutter/material.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:oktoast/oktoast.dart';
import 'package:flutter/foundation.dart';

class Info {
  // URL launch management
  void _launchURL(Uri url, BuildContext context) async {
    if (await canLaunchUrl(url)) {
      await launchUrl(
        url,
        mode: LaunchMode.externalApplication,
      );
    } else {
      showToast(AppLocalizations.of(context)!.info_exception_linkopenfailed +
          url.toString());
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

        if (snapshot.hasError) {
          packageInfo = AppLocalizations.of(context)!.info_packageinfofail;
        } else if (snapshot.hasData) {
          appName = snapshot.data?.appName ?? '(null)';
          version = snapshot.data?.version ?? '(null)';
          packageInfo = '$appName v$version';
        } else {
          packageInfo = AppLocalizations.of(context)!.info_pending_appinfo;
        }

        return Text(packageInfo);
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
      BuildContext context, Widget packageInfo, BuildContext contextDialog) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Align(
          alignment: Alignment.centerLeft,
          child: Container(
            child: Text(
              AppLocalizations.of(context)!.info_title,
              style: const TextStyle(
                fontSize: 25.0,
              ),
            ),
          ),
        ),
        const SizedBox(height: 2),
        Align(
          alignment: Alignment.centerLeft,
          child: Container(
            child: Row(
              children: [
                packageInfo,
                Text(' ('),
                Text(kReleaseMode ? 'release' : 'debug'),
                Text(')'),
              ],
            ),
          ),
        ),
        const SizedBox(height: 10),
        Column(
          mainAxisAlignment: MainAxisAlignment.end,
          children: [
            ElevatedButton(
              onPressed: () {
                _launchURL(
                  Uri(
                      scheme: 'https',
                      host: 'github.com',
                      path: 'uintdev/qrserv'),
                  context,
                );
              },
              child: Row(
                children: [
                  Icon(Icons.code),
                  const SizedBox(width: 10),
                  Row(
                    children: [
                      Text(
                        AppLocalizations.of(context)!.info_opensource_title,
                      ),
                      Text(
                        ' (GitHub)',
                      ),
                    ],
                  ),
                ],
              ),
            ),
            ElevatedButton(
              onPressed: () {
                _launchURL(
                    Uri(scheme: 'https', host: 'ko-fi.com', path: 'uintdev'),
                    context);
              },
              child: Row(
                children: [
                  Icon(Icons.local_cafe),
                  const SizedBox(width: 10),
                  Row(
                    children: [
                      Text(
                        AppLocalizations.of(context)!.info_donate_title,
                      ),
                      Text(' (Ko-fi)'),
                    ],
                  )
                ],
              ),
            ),
            const SizedBox(height: 8),
            TextButton(
              onPressed: () {
                Navigator.pop(contextDialog);
              },
              child: Text(AppLocalizations.of(context)!.info_close),
            ),
          ],
        ),
      ],
    );
  }
}
