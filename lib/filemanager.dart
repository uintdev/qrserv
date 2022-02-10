import 'dart:io';
import 'package:flutter/material.dart';
import 'package:file_picker/file_picker.dart';
import 'package:path/path.dart';
import 'cachemanager.dart';
import 'server.dart';

class FileManager {
  static String currentFile = '';
  static String currentFullPath = '';
  static String currentPath = '';
  static int currentLength = 0;

  static bool fileImported = false;
  static bool multipleFiles = false;
  final bool allowMultipleFiles = (Platform.isAndroid);

  Map<String, dynamic> readInfo() {
    return {
      'name': currentFile,
      'path': currentFullPath,
      'pathpart': currentPath,
      'length': currentLength
    };
  }

  Future selectFile(BuildContext context) async {
    FilePickerResult? result =
        await FilePicker.platform.pickFiles(allowMultiple: allowMultipleFiles);

    if (result != null) {
      List<Map<String, dynamic>> selectedFiles = [];

      multipleFiles = (result.files.length > 1);

      for (int i = 0; i < result.files.length; i++) {
        selectedFiles.add({
          'name': result.files[i].name,
          'path': result.files[i].path,
          'length': result.files[i].size
        });
      }

      if (multipleFiles) {
        // TODO: implement zip functionality, remove each file as they get added from cache, set archive as main file, list added files in tooltip
      } else {
        // Cache handling
        if (currentFullPath != '' &&
            currentFullPath != selectedFiles.first['path'] &&
            Server().fileExists(currentFullPath)) {
          CacheManager().deleteCache(context, currentFullPath);
        }

        // Set file information
        FileManager.currentFile = selectedFiles.first['name'];
        FileManager.currentFullPath = selectedFiles.first['path'];
        FileManager.currentPath = dirname(selectedFiles.first['path']);
        FileManager.currentLength = selectedFiles.first['length'];
        FileManager.fileImported = true;
      }
    }
  }
}
