//import 'dart:async';
//import 'dart:io';
import 'package:flutter/material.dart';
import '../l10n/generated/app_localizations.dart';
//import 'package:oktoast/oktoast.dart';

class SettingsPage extends StatefulWidget {
  @override
  Settings createState() => Settings();
}

class Settings extends State<SettingsPage> {
  @override
  Widget build(BuildContext context) {
    return SettingsContent(context);
  }
}

Scaffold SettingsContent(BuildContext context) {
  return Scaffold(
    backgroundColor: Theme.of(context).canvasColor,
    body: CustomScrollView(
      slivers: <Widget>[
        SliverAppBar(
          iconTheme: IconThemeData(
            color: Theme.of(context).textTheme.titleLarge?.color,
          ),
          expandedHeight: 195.0,
          floating: false,
          pinned: true,
          elevation: 0,
          shadowColor: Colors.transparent,
          foregroundColor: Theme.of(context).textTheme.titleLarge?.color,
          backgroundColor: Theme.of(context).canvasColor,
          surfaceTintColor: Colors.transparent,
          flexibleSpace: FlexibleSpaceBar(
            title: Text(
              AppLocalizations.of(context)!.settings_title,
              style: const TextStyle(
                fontVariations: [FontVariation('wght', 500)],
              ),
            ),
            expandedTitleScale: 2.3,
            background: Container(color: Theme.of(context).canvasColor),
          ),
        ),
        SliverFillRemaining(child: SettingsBody(context)),
      ],
    ),
  );
}

Column SettingsBody(BuildContext context) {
  return Column(
    crossAxisAlignment: .start,
    children: [
      SizedBox(height: 15),
      Padding(
        padding: .fromLTRB(30, 0, 30, 0),
        // TODO: Use localized strings
        child: Text(
          'Server',
          style: const TextStyle(
            fontVariations: [FontVariation('wght', 500)],
            fontSize: 14,
          ),
          textAlign: .left,
        ),
      ),
      SizedBox(height: 10),
      // TODO: to be implemented
      ListTileEntry(
        context,
        Text('Port number'),
        Text('Port number to use for the HTTP server'),
        () {
          print('Option had been pressed.');
        },
      ),
    ],
  );
}

Padding ListTileEntry(
  BuildContext context,
  Widget? title,
  Widget? subtitle,
  Function()? onTap,
) {
  return Padding(
    padding: .fromLTRB(20, 0, 20, 0),
    // TODO: Use localized strings
    child: Material(
      child: ListTile(
        tileColor: Theme.of(context).cardTheme.color,
        contentPadding: EdgeInsets.symmetric(horizontal: 16.0, vertical: 3.0),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(25),
          // borderRadius: BorderRadius.only(
          //   topLeft: Radius.circular(25),
          //   topRight: Radius.circular(25),
          // ),
        ),
        title: title,
        subtitle: subtitle,
        subtitleTextStyle: const TextStyle(
          fontVariations: [FontVariation('wght', 300)],
        ),
        onTap: onTap,
      ),
    ),
  );
}
