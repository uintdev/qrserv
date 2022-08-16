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
      // TODO: there are prints here!

      // Recursive file removal
      String cacheDir = await FileManager().filePickerPath();
      Directory cachePath = new Directory(cacheDir);

      if (cachePath.existsSync()) {
        print('TO EXCLUDE: ' + file.toString());
        cachePath.listSync().forEach((e) {
          print('FOUND: ' + e.path);
          if (!file.contains(e.path)) {
            print('DELETED: ' + e.path);
            e.deleteSync(recursive: true);
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
  }
}
