import 'dart:io';
import 'package:path_provider/path_provider.dart';
import 'package:oktoast/oktoast.dart';
import 'package:flutter_translate/flutter_translate.dart';

class CacheManager {
  Future<void> deleteCache([String file = ""]) async {
    // Android only
    if (!Platform.isAndroid) {
      return;
    }

    if (file == '') {
      // Recursive file removal
      String cacheDir = (await getTemporaryDirectory()).path;
      Directory cachePath = new Directory(cacheDir);

      if (cachePath.existsSync()) {
        cachePath.deleteSync(recursive: true);
      }
    } else {
      // Individual file removal
      String cacheDir =
          (await getTemporaryDirectory()).path + '/file_picker/' + file;
      File cachePath = new File(cacheDir);

      try {
        await cachePath.delete();
      } catch (e) {
        showToast(translate('info.exception.fileremoval.msg') + e.toString());
      }
    }
  }
}
