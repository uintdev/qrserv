import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:oktoast/oktoast.dart';
import 'statemanager.dart';
import 'filemanager.dart';

class CacheManager {
  static bool cacheDeleteDir = false;
  static bool cacheDeleteSpecific = false;

  Future<void> deleteCache(BuildContext context,
      [List<String> file = const [], bool exclude = false]) async {
    // Disallow desktop platforms
    if (StateManager().isDesktop) return;

    if (file.length == 0 || file.length > 0 && exclude) {
      if (cacheDeleteDir) return;
      cacheDeleteDir = true;
      // Reset archivedLast state
      if (file.length == 0) FileManager.archivedLast = '';

      // Recursive file removal
      String cacheDir = await FileManager().filePickerPath();
      Directory cachePath = Directory(cacheDir);

      if (await cachePath.exists()) {
        await cachePath.list().forEach((e) async {
          if (!file.contains(e.path)) {
            await e.delete(recursive: true);
          }
        });
      }
      cacheDeleteDir = false;
    } else {
      if (cacheDeleteSpecific) return;
      cacheDeleteSpecific = true;
      // Individual file removal
      List<String> cacheDir = file;

      for (int i = 0; i < cacheDir.length; i++) {
        File cachePath = File(cacheDir[i]);
        try {
          await cachePath.delete();
        } catch (e) {
          showToast(AppLocalizations.of(context)!.info_exception_fileremoval +
              e.toString());
        }
      }
      cacheDeleteSpecific = false;
    }
    RebuildNotification().dispatch(context);
  }
}
