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
    FilePickerResult? result = await FilePicker.platform.pickFiles(
        allowMultiple: allowMultipleFiles,
        // withReadStream: true,
        onFileLoading: (selectionStatus) {
          print(selectionStatus);
        });
    print(result);

    if (result != null) {
      print(result.files);
      print(result.files.length);

      multipleFiles = (result.files.length > 1);

      for (int i = 0; i < result.files.length; i++) {
        print('-------- FILE --------');
        print(result.files[i].name);
        print(result.files[i].path);
        print(result.files[i].size);
      }
      print(multipleFiles);

      if (multipleFiles) {
        // TODO: implement zip functionality, remove each file as they get added from cache, set archive as main file, list added files in tooltip
      } else {
        // Cache handling
        if (currentFullPath != '' &&
            currentFullPath != (result.files.first.path ?? '') &&
            Server().fileExists(currentFullPath)) {
          CacheManager().deleteCache(context, currentFullPath);
        }

        // if (result.files.first.readStream != null) {
        //   // TODO: add storage check
        //   StorageSpace freeSpace = await getStorageSpace(
        //     lowOnSpaceThreshold: 50 * 1024 * 1024, // 2GB
        //     fractionDigits:
        //         1, // How many digits to use for the human-readable values
        //   );

        //   print(freeSpace.free);

        //   int fileSize = 0;
        //   // double percentageProgress = 0;
        //   var test = result.files.first.readStream!
        //       .listen((event) {
        //         print(event.length);
        //         fileSize = (fileSize + event.length);
        //         // percentageProgress = (fileSize / result.files.first.size) * 100;
        //         // print(percentageProgress.toString() + '%');
        //       })
        //       .asFuture()
        //       .catchError((e) => print(e))
        //       .then((value) {
        //         print(fileSize);
        //       });
        //   print(test);
        //   // var test = result.files.first.readStream!.transform(UTF8.decoder).listen((event) {
        //   //   print(event);
        //   // });
        // }

        // Set file information
        FileManager.currentFile = result.files.first.name;
        FileManager.currentFullPath = result.files.first.path ?? '';
        FileManager.currentPath = dirname(result.files.first.path ?? '');
        FileManager.currentLength = result.files.first.size;
        FileManager.fileImported = true;
      }
    }
  }
}
