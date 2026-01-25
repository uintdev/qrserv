import 'dart:io';
import 'package:flutter/material.dart';
import 'package:qrserv/components/preferences.dart';
import 'package:device_info_plus/device_info_plus.dart';
import '../../components/filemanager.dart';

class DAM {
  Future<bool> eligibility() async {
    if (!Platform.isAndroid) return false;
    if (!FileManager().isPlayStoreFriendly) return true;

    // Does not require MES permission on Android 10 or lower
    final androidInfo = await DeviceInfoPlugin().androidInfo;
    return androidInfo.version.sdkInt <=
        FileManager().directAccessModeNoMESMaxAPI;
  }

  Future<void> toggle(BuildContext context, StateSetter setState) async {
    FileManager.directAccessMode = !FileManager.directAccessMode;
    await Preferences().write(
      Preferences.PREF_CLIENT_DAM,
      FileManager.directAccessMode,
    );
    setState(() {});
  }
}
