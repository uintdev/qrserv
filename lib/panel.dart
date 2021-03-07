import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:oktoast/oktoast.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';

class Panel {
  // URL launch management
  _launchURL(String url, BuildContext context) async {
    if (await canLaunch(url)) {
      await launch(url);
    } else {
      showToast(AppLocalizations.of(context)!.info_exception_linkopenfailed);
    }
  }

  // Clickable card template
  Widget cardClickable(IconData iconData, String title, String subtitle,
      String url, BuildContext context) {
    return Card(
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(20.0),
      ),
      elevation: 1,
      child: ListTile(
        visualDensity: VisualDensity(horizontal: 0, vertical: -3),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(20.0),
        ),
        contentPadding: const EdgeInsets.fromLTRB(30, 16, 30, 16),
        leading: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            Icon(
              iconData,
              color: Colors.white,
            ),
          ],
        ),
        title: Text(title),
        subtitle: Text(subtitle),
        onTap: () {
          _launchURL(url, context);
        },
      ),
    );
  }

  // Panel header
  Widget panelHeader(BuildContext context) {
    return Container(
      width: MediaQuery.of(context).size.width,
      child: Center(
        child: Column(
          children: <Widget>[
            Container(
              height: 45.0,
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: <Widget>[
                  Container(
                    width: 30,
                    height: 5,
                    decoration: BoxDecoration(
                        color: Colors.grey[300],
                        borderRadius: BorderRadius.all(Radius.circular(12.0))),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  // Panel interface
  Widget panelInterface(ScrollController sc, BuildContext context) {
    return FutureBuilder<PackageInfo>(
      future: PackageInfo.fromPlatform(),
      builder: (context, AsyncSnapshot<PackageInfo> snapshot) {
        String packageInfo = '';
        String appName = '';
        String version = '';

        if (snapshot.hasError) {
          packageInfo = AppLocalizations.of(context)!.panel_packageinfofail;
        } else if (snapshot.hasData) {
          if (snapshot.data?.appName == null ||
              snapshot.data?.version == null) {
            appName = '(unknown)';
            version = '(unknown)';
          } else {
            appName = snapshot.data!.appName;
            version = snapshot.data!.version;
          }
          packageInfo = '$appName v$version';
        } else {
          packageInfo = AppLocalizations.of(context)!.info_pending_appinfo;
        }

        return MediaQuery.removePadding(
          context: context,
          removeTop: true,
          child: ListView(
            controller: sc,
            padding: const EdgeInsets.only(left: 24.0, right: 24.0),
            children: <Widget>[
              SizedBox(
                height: 70.0,
              ),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: <Widget>[
                  Text(
                    AppLocalizations.of(context)!.panel_title,
                    style: TextStyle(
                      fontWeight: FontWeight.normal,
                      fontSize: 24.0,
                    ),
                  ),
                ],
              ),
              SizedBox(
                height: 30.0,
              ),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: <Widget>[
                  Text(
                    packageInfo,
                    style: TextStyle(
                      fontWeight: FontWeight.normal,
                      fontSize: 12.3,
                    ),
                  ),
                ],
              ),
              SizedBox(
                height: 30.0,
              ),
              cardClickable(
                Icons.code,
                AppLocalizations.of(context)!.panel_card_opensource_title,
                AppLocalizations.of(context)!.panel_card_opensource_subtitle,
                'https://github.com/uintdev/qrserv',
                context,
              ),
              SizedBox(
                height: 5.0,
              ),
              cardClickable(
                Icons.local_cafe,
                AppLocalizations.of(context)!.panel_card_donate_title,
                AppLocalizations.of(context)!.panel_card_donate_subtitle,
                'https://ko-fi.com/uintdev',
                context,
              ),
              SizedBox(
                height: 25.0,
              ),
            ],
          ),
        );
      },
    );
  }
}
