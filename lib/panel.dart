import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:oktoast/oktoast.dart';
import 'package:flutter_translate/flutter_translate.dart';

class Panel {
  // URL launch management
  _launchURL(String url) async {
    if (await canLaunch(url)) {
      await launch(url);
    } else {
      showToast(translate('info.exception.linkopenfailed.msg'));
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
          _launchURL(url);
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
          packageInfo = translate('panel.packageinfofail.msg');
        } else if (snapshot.hasData) {
          if (snapshot.data == null) {
            appName = '(null)';
            version = '(null)';
          } else {
            appName = snapshot.data.appName;
            version = snapshot.data.version;
          }
          packageInfo = '$appName v$version';
        } else {
          packageInfo = translate('info.pending.appinfo.msg');
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
                    translate('panel.title.msg'),
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
                translate('panel.card.opensource.title'),
                translate('panel.card.opensource.subtitle'),
                'https://github.com/uintdev/qrserv',
                context,
              ),
              SizedBox(
                height: 5.0,
              ),
              cardClickable(
                Icons.local_cafe,
                translate('panel.card.donate.title'),
                translate('panel.card.donate.subtitle'),
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
