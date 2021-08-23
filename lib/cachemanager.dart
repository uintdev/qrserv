import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:path_provider/path_provider.dart';
import 'package:oktoast/oktoast.dart';

class CacheManager {
  Future<void> deleteCache(BuildContext context,
      [String file = '', bool exclude = false]) async {
    if (file == '' || file != '' && exclude) {
      // Recursive file removal
      String cacheDir = (await getTemporaryDirectory()).path;
      Directory cachePath = new Directory(cacheDir);

      if (cachePath.existsSync()) {
        cachePath.listSync().forEach((e) {
          if (e.path != file && !exclude) {
            e.deleteSync(recursive: true);
          }
        });
      }
    } else {
      // Individual file removal
      String cacheDir = file;
      File cachePath = new File(cacheDir);

      try {
        await cachePath.delete();
      } catch (e) {
        showToast(AppLocalizations.of(context)!.info_exception_fileremoval +
            e.toString());
      }
    }
  }
}
