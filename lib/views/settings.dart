import 'package:flutter/material.dart';
import 'package:qrserv/components/preferences.dart';
import '../l10n/generated/app_localizations.dart';
import 'package:oktoast/oktoast.dart';
import 'settings/port.dart';

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
    body: NestedScrollView(
      headerSliverBuilder: (BuildContext context, bool innerBoxIsScrolled) {
        return <Widget>[
          SliverOverlapAbsorber(
            handle: NestedScrollView.sliverOverlapAbsorberHandleFor(context),
            sliver: SliverAppBar(
              iconTheme: IconThemeData(
                color: Theme.of(context).textTheme.titleLarge?.color,
              ),
              expandedHeight: 195.0,
              floating: false,
              pinned: true,
              elevation: 0,
              shadowColor: Colors.transparent,
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
          ),
        ];
      },
      body: Builder(
        builder: (BuildContext context) {
          return CustomScrollView(
            slivers: [
              SliverOverlapInjector(
                handle: NestedScrollView.sliverOverlapAbsorberHandleFor(
                  context,
                ),
              ),
              SliverToBoxAdapter(child: SettingsBody(context)),
            ],
          );
        },
      ),
    ),
  );
}

Column SettingsBody(BuildContext context) {
  return Column(
    crossAxisAlignment: .start,
    children: [
      ListSubheader(
        context,
        AppLocalizations.of(context)!.settings_subheading_server,
        true,
      ),
      ListTileEntry(
        context,
        Text(AppLocalizations.of(context)!.settings_server_port_list_title),
        Text(AppLocalizations.of(context)!.settings_server_port_list_subtitle),
        () async {
          await Port().portDialog(context);
        },
      ),
      // TODO: Use localized strings
      ListSubheader(context, 'General'),
      ListTileEntry(context, Text('Reset to defaults'), null, () async {
        await Preferences().clear();
        // TODO: Use localized strings
        showToast('Settings had been reset to defaults');
      }),
      // TODO: other options to be determined
    ],
  );
}

Column ListSubheader(
  BuildContext context,
  String subheading, [
  bool initial = false,
]) {
  double initialPadding = 20;
  if (initial) {
    initialPadding = 0;
  }
  return Column(
    children: [
      SizedBox(height: initialPadding),
      Padding(
        padding: .fromLTRB(30, 0, 30, 0),
        child: Text(
          subheading,
          style: const TextStyle(
            fontVariations: [FontVariation('wght', 700)],
            fontSize: 14,
          ),
          textAlign: .left,
        ),
      ),
      SizedBox(height: 10),
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
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 16.0,
          vertical: 3.0,
        ),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(25),
          // borderRadius: BorderRadius.only(
          //   topLeft: Radius.circular(25),
          //   topRight: Radius.circular(25),
          // ),
        ),
        title: title,
        subtitle: (subtitle != null)
            ? Opacity(opacity: 0.6, child: subtitle)
            : subtitle,
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
