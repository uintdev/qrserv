import 'package:flutter/material.dart';
import 'package:qrserv/components/preferences.dart';
import '../l10n/generated/app_localizations.dart';
import 'package:oktoast/oktoast.dart';
import 'settings/port.dart';
import 'settings/dam.dart';
import 'settings/fiu.dart';
import '../components/filemanager.dart';

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
      body: StatefulBuilder(
        builder: (BuildContext context, StateSetter setState) {
          return CustomScrollView(
            slivers: [
              SliverOverlapInjector(
                handle: NestedScrollView.sliverOverlapAbsorberHandleFor(
                  context,
                ),
              ),
              SliverToBoxAdapter(child: SettingsBody(context, setState)),
            ],
          );
        },
      ),
    ),
  );
}

Column SettingsBody(BuildContext context, StateSetter setState) {
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
        AppLocalizations.of(context)!.settings_server_port_list_title,
        AppLocalizations.of(context)!.settings_server_port_list_subtitle,
        () async {
          await Port().portDialog(context);
        },
      ),
      ListSubheader(
        context,
        AppLocalizations.of(context)!.settings_subheading_client,
      ),
      ListTileEntry(
        context,
        AppLocalizations.of(context)!.settings_client_dam_list_title,
        AppLocalizations.of(context)!.settings_client_dam_list_subtitle,
        () async {
          final bool damEligibility = await DAM.eligibility();
          if (!damEligibility) {
            showToast(
              AppLocalizations.of(context)!.settings_client_dam_ineligiblebuild,
            );
            return;
          }
          DAM.toggle(context, setState);
        },
        FileManager.directAccessMode,
      ),
      SizedBox(height: 10),
      ListTileEntry(
        context,
        AppLocalizations.of(context)!.settings_client_fiu_list_title,
        AppLocalizations.of(context)!.settings_client_fiu_list_subtitle,
        () async {
          FIU.toggle(context, setState);
        },
        FIU.state,
      ),
      ListSubheader(
        context,
        AppLocalizations.of(context)!.settings_subheading_general,
      ),
      ListTileEntry(
        context,
        AppLocalizations.of(context)!.settings_general_defaults_list_title,
        null,
        () async {
          setState(() async {
            await Preferences.clear(() {
              FileManager.directAccessMode = false;
              FIU.state = false;
            });
          });
          showToast(
            AppLocalizations.of(context)!.settings_general_defaults_success,
          );
        },
      ),
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
  String title,
  String? subtitle,
  Function()? onTap, [
  bool? switchValue = null,
]) {
  return Padding(
    padding: .fromLTRB(20, 0, 20, 0),
    child: Material(
      elevation: 1,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(25)),
      clipBehavior: .antiAlias,
      child: ListTile(
        textColor: Theme.of(context).primaryColor,
        tileColor: Theme.of(context).cardTheme.color,
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 16.0,
          vertical: 3.0,
        ),
        title: Text(title),
        subtitle: (subtitle != null)
            ? Opacity(opacity: 0.6, child: Text(subtitle))
            : null,
        titleTextStyle: const TextStyle(
          fontSize: 16.0,
          fontVariations: [FontVariation('wght', 600)],
        ),
        subtitleTextStyle: const TextStyle(
          fontVariations: [FontVariation('wght', 400)],
        ),
        trailing: (switchValue != null)
            ? Switch(
                value: switchValue,
                onChanged: null,
                inactiveTrackColor: Theme.of(context).canvasColor,
                thumbColor: WidgetStateProperty.all(
                  switchValue ? Theme.of(context).cardTheme.color : null,
                ),
                activeTrackColor: Theme.of(
                  context,
                ).colorScheme.secondaryContainer,
              )
            : null,
        onTap: onTap,
      ),
    ),
  );
}
