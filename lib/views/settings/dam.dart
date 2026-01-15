import 'dart:io';
import 'package:flutter/material.dart';
import 'package:qrserv/components/preferences.dart';
import '../../l10n/generated/app_localizations.dart';
import 'package:oktoast/oktoast.dart';
import 'package:device_info_plus/device_info_plus.dart';
import '../../components/filemanager.dart';

class DAM {
  Future<bool> eligibility() async {
    bool result = false;
    if (Platform.isAndroid) {
      if (FileManager().isPlayStoreFriendly) {
        final androidInfo = await DeviceInfoPlugin().androidInfo;
        if (androidInfo.version.sdkInt <=
            FileManager().directAccessModeNoMESMaxAPI) {
          // Does not require MES permission on Android 10 or lower
          result = true;
        }
      } else {
        result = true;
      }
    }
    return result;
  }

  Future<void> toggle(BuildContext context, StateSetter setState) async {
    !FileManager.directAccessMode
        ? showToast(AppLocalizations.of(context)!.dam_state_enabled)
        : showToast(AppLocalizations.of(context)!.dam_state_disabled);
    setState(() async {
      FileManager.directAccessMode = !FileManager.directAccessMode;
      await Preferences().write(
        Preferences.PREF_CLIENT_DAM,
        FileManager.directAccessMode,
      );
    });
  }
}
