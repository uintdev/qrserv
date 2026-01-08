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
        padding: .fromLTRB(20, 0, 20, 0),
        // TODO: Use localized strings
        child: Text(
          'Server -- WIP',
          style: const TextStyle(
            fontVariations: [FontVariation('wght', 500)],
            fontSize: 16,
          ),
          textAlign: .left,
        ),
      ),
      // TODO: to be implemented
    ],
  );
}
