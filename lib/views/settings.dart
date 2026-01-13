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
          AppLocalizations.of(context)!.settings_subheading_server,
          style: const TextStyle(
            fontVariations: [FontVariation('wght', 700)],
            fontSize: 14,
          ),
          textAlign: .left,
        ),
      ),
      SizedBox(height: 10),
      // TODO: Use localized strings
      ListTileEntry(
        context,
        Text(
          AppLocalizations.of(context)!.settings_server_port_number_list_title,
        ),
        Text(
          AppLocalizations.of(
            context,
          )!.settings_server_port_number_list_subtitle,
        ),
        () {
          print('Option had been pressed.');
        },
      ),
      // TODO: to be implemented
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
        subtitle: Opacity(opacity: 0.6, child: subtitle),
        titleTextStyle: const TextStyle(
          fontSize: 16.0,
          fontVariations: [FontVariation('wght', 600)],
        ),
        subtitleTextStyle: const TextStyle(
          fontVariations: [FontVariation('wght', 400)],
        ),
        onTap: onTap,
      ),
    ),
  );
}
