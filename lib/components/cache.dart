import 'dart:io';
import 'package:flutter/material.dart';
import '../l10n/generated/app_localizations.dart';
import 'package:oktoast/oktoast.dart';
import '../views/statemanager.dart';
import 'filemanager.dart';

class CacheManager {
  static bool cacheDeleteDir = false;
  static bool cacheDeleteSpecific = false;

  Future<void> deleteCache(
    BuildContext context, [
    List<String> file = const [],
    bool exclude = false,
  ]) async {
    // Disallow desktop platforms
    if (StateManager().isDesktop) return;

    if (file.length == 0 || file.length > 0 && exclude) {
      if (cacheDeleteDir) return;
      cacheDeleteDir = true;
      // Reset archivedLast state
      if (file.length == 0) FileManager.archivedLast = '';

      // Recursive file removal
      String pickerDir = await FileManager().filePickerPath();
      Directory pickerPath = Directory(pickerDir);

      if (await pickerPath.exists()) {
        await pickerPath.list().forEach((e) async {
          if (!file.contains(e.path)) {
            if (FileManager().directModeDetect(e.path)) return;
            await e.delete(recursive: true);
          }
        });
      }
      cacheDeleteDir = false;
    } else {
      if (cacheDeleteSpecific) return;
      cacheDeleteSpecific = true;
      // Individual file removal
      List<String> pickerDir = file;

      for (int i = 0; i < pickerDir.length; i++) {
        if (FileManager().directModeDetect(pickerDir[i])) continue;

        File pickerPath = File(pickerDir[i]);

        try {
          await pickerPath.delete();
        } catch (e) {
          showToast(
            AppLocalizations.of(context)!.info_exception_fileremoval +
                e.toString(),
          );
        }
      }
      cacheDeleteSpecific = false;
    }
    RebuildNotification().dispatch(context);
  }
}
