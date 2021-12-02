import 'package:flutter/material.dart';
import 'package:file_picker_cross/file_picker_cross.dart';
import 'package:path/path.dart';
import 'cachemanager.dart';
import 'server.dart';

class FilePicker {
  static String currentFile = '';
  static String currentFullPath = '';
  static String currentPath = '';
  static int currentLength = 0;

  static bool fileImported = false;

  Map<String, dynamic> readInfo() {
    return {
      'name': currentFile,
      'path': currentFullPath,
      'pathpart': currentPath,
      'length': currentLength
    };
  }

  Future selectFile(BuildContext context) async {
    await FilePickerCross.importFromStorage().then((file) {
      // Cache handling
      if (currentFullPath != '' &&
          currentFullPath != file.path &&
          Server().fileExists(currentFullPath)) {
        CacheManager().deleteCache(context, currentFullPath);
      }

      // Set file information
      FilePicker.currentFile = file.fileName ?? '';
      FilePicker.currentFullPath = file.path ?? '';
      FilePicker.currentPath = dirname(file.path ?? '');
      FilePicker.currentLength = file.length;

      // Set import status
      FilePicker.fileImported = true;
    });
  }
}
