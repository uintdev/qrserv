import 'dart:io';
import 'package:material_ui/material_ui.dart';
import '../l10n/generated/app_localizations.dart';
import 'package:oktoast/oktoast.dart';
import '../views/statemanager.dart';
import 'filemanager.dart';

class CacheManager {
  // Private constructor to prevent instantiation
  CacheManager._();

  static bool cacheDeleteDir = false;
  static bool cacheDeleteSpecific = false;

  static Future<void> deleteCache(
    BuildContext context, [
    List<String> file = const [],
    bool exclude = false,
    bool ignoreDAM = false,
  ]) async {
    if (file.isEmpty || exclude) {
      if (cacheDeleteDir) return;
      cacheDeleteDir = true;
      // Reset archivedLast state
      if (file.isEmpty) FileManager.archivedLast = '';

      // Recursive file removal
      final String pickerDir = await FileManager.filePickerPath(ignoreDAM);
      final Directory pickerPath = Directory(pickerDir);

      if (await pickerPath.exists()) {
        await for (final entity in pickerPath.list()) {
          if (file.contains(entity.path)) continue;
          if (FileManager.directModeDetect(entity.path)) continue;
          await entity.delete(recursive: true);
        }
      }
      cacheDeleteDir = false;
    } else {
      if (cacheDeleteSpecific) return;
      cacheDeleteSpecific = true;
      // Individual file removal
      for (final path in file) {
        if (FileManager.directModeDetect(path)) continue;

        try {
          await File(path).delete();
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
