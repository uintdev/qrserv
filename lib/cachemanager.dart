import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:oktoast/oktoast.dart';
import 'statemanager.dart';
import 'filemanager.dart';

class CacheManager {
  Future<void> deleteCache(BuildContext context,
      [List<String> file = const [], bool exclude = false]) async {
    // Disallow desktop platforms
    if (StateManager().isDesktop) return;

    if (file.length == 0 || file.length > 0 && exclude) {
      // Reset archivedLast state
      if (file.length == 0) FileManager.archivedLast = '';

      // Recursive file removal
      String cacheDir = await FileManager().filePickerPath();
      Directory cachePath = new Directory(cacheDir);

      if (await cachePath.exists()) {
        await cachePath.list().forEach((e) async {
          if (!file.contains(e.path)) {
            File filePath = new File(e.path);
            await filePath.exists().then((fileExists) async {
              if (fileExists) await e.delete(recursive: true);
            });
          }
        });
      }
    } else {
      // Individual file removal
      List<String> cacheDir = file;

      for (int i = 0; i < cacheDir.length; i++) {
        File cachePath = new File(cacheDir[i]);
        try {
          await cachePath.delete();
        } catch (e) {
          showToast(AppLocalizations.of(context)!.info_exception_fileremoval +
              e.toString());
        }
      }
    }
    RebuildNotification().dispatch(context);
  }
}
