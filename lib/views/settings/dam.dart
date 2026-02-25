import 'package:flutter/material.dart';
import 'package:qrserv/components/preferences.dart';
import 'package:device_info_plus/device_info_plus.dart';
import '../../components/filemanager.dart';

class DAM {
  // Private constructor to prevent instantiation
  DAM._();

  static Future<bool> eligibility() async {
    if (!FileManager.isPlayStoreFriendly) return true;

    // Does not require MES permission on Android 10 or lower
    final androidInfo = await DeviceInfoPlugin().androidInfo;
    return androidInfo.version.sdkInt <=
        FileManager.directAccessModeNoMESMaxAPI;
  }

  static Future<void> toggle(BuildContext context, StateSetter setState) async {
    FileManager.directAccessMode = !FileManager.directAccessMode;
    await Preferences.write(
      Preferences.PREF_CLIENT_DAM,
      FileManager.directAccessMode,
    );
    setState(() {});
  }
}
