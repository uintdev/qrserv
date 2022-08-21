import 'dart:io';
import 'package:path_provider/path_provider.dart';
import 'package:flutter/material.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:filesize/filesize.dart';
import 'package:file_picker/file_picker.dart';
import 'package:async_zip/async_zip.dart';
import 'package:path/path.dart';
import 'package:oktoast/oktoast.dart';
import 'cachemanager.dart';
import 'server.dart';

class FileManager {
  static String currentFile = '';
  static String currentFullPath = '';
  static String currentPath = '';
  static int currentLength = 0;
  static List archivedFiles = [];
  static String archivedLast = '';

  static bool fileImported = false;
  static bool multipleFiles = false;
  static bool allowWatcher = false;
  final bool allowMultipleFiles = (Platform.isAndroid);

  Map<String, dynamic> readInfo() {
    return {
      'name': currentFile,
      'path': currentFullPath,
      'pathpart': currentPath,
      'length': currentLength,
      'archived': archivedFiles
    };
  }

  Future<String> filePickerPath() async {
    String cacheDir = (await getTemporaryDirectory()).path + '/file_picker';
    return cacheDir;
  }

  String fileSizeHuman(length, round, context) {
    String _sizeHuman = filesize(length, round);
    _sizeHuman = _sizeHuman.replaceAll(
        'TB', AppLocalizations.of(context)!.page_imported_sizesymbol_tb);
    _sizeHuman = _sizeHuman.replaceAll(
        'GB', AppLocalizations.of(context)!.page_imported_sizesymbol_gb);
    _sizeHuman = _sizeHuman.replaceAll(
        'MB', AppLocalizations.of(context)!.page_imported_sizesymbol_mb);
    _sizeHuman = _sizeHuman.replaceAll(
        'KB', AppLocalizations.of(context)!.page_imported_sizesymbol_kb);
    _sizeHuman = _sizeHuman.replaceAll(
        ' B', ' ' + AppLocalizations.of(context)!.page_imported_sizesymbol_b);
    _sizeHuman = _sizeHuman.replaceAll(
        '.', AppLocalizations.of(context)!.page_imported_decimalseparator);

    return _sizeHuman;
  }

  Future selectFile(BuildContext context) async {
    FilePickerResult? result =
        await FilePicker.platform.pickFiles(allowMultiple: allowMultipleFiles);

    if (result != null) {
      FileManager.allowWatcher = false;

      multipleFiles = (result.files.length > 1);

      List<String> cacheExceptionList = [];
      for (int i = 0; i < result.files.length; i++) {
        archivedFiles
            .add({'file': result.files[i].name, 'size': result.files[i].size});
        cacheExceptionList.add(result.files[i].path ?? '');
      }

      // Empty last archive entry if current selection does not contain multiple files
      if (!multipleFiles) archivedLast = '';

      // Exclude previously created archive (if any) initially
      if (archivedLast != '') {
        cacheExceptionList.add(archivedLast);
      }

      // Cache handling
      await CacheManager().deleteCache(context, cacheExceptionList, true);

      String _currentFile = '';
      String _currentFullPath = '';
      String _currentPath = '';
      int _currentLength = 0;

      if (multipleFiles) {
        // Prepare archive name
        String archiveName =
            Server().tokenGenerator(characters: 'ABCDEF1234567890', length: 8) +
                '.zip';
        String fullPickerPath = await filePickerPath();
        String fullArchivePath = fullPickerPath + '/' + archiveName;

        final archiveWriter = ZipFileWriterAsync();
        try {
          await archiveWriter.create(File(fullArchivePath));

          // Write a file to the archive
          for (int i = 0; i < result.files.length; i++) {
            showToast('File ' +
                (i + 1).toString() +
                ' out of ' +
                result.files.length.toString());
            await File(result.files[i].path ??
                    fullPickerPath + '/' + result.files[i].name)
                .exists()
                .then((_) async {
              await archiveWriter.writeFile(
                  result.files[i].name,
                  File(result.files[i].path ??
                      fullPickerPath + '/' + result.files[i].name));
              await File(result.files[i].path ??
                      fullPickerPath + '/' + result.files[i].name)
                  .delete();
            });
          }
        } on ZipException catch (ex) {
          showToast(AppLocalizations.of(context)!.page_imported_archive_failed +
              ex.message);
          await archiveWriter.close();
          return;
        }

        await archiveWriter.close();

        // Get length of created archive
        int archiveSize = await File(fullArchivePath).length();

        _currentFile = archiveName;
        _currentFullPath = fullArchivePath;
        _currentPath = fullPickerPath;
        _currentLength = archiveSize;

        if (archivedLast != '') {
          await CacheManager().deleteCache(context, [archivedLast]);
        }

        archivedLast = _currentFullPath;
      } else {
        _currentFile = result.files.first.name;
        _currentFullPath = result.files.first.path ?? '';
        _currentPath = dirname(result.files.first.path ?? '');
        _currentLength = result.files.first.size;
        archivedFiles = [];
        archivedLast = '';
      }

      // Set file information
      FileManager.currentFile = _currentFile;
      FileManager.currentFullPath = _currentFullPath;
      FileManager.currentPath = _currentPath;
      FileManager.currentLength = _currentLength;
      FileManager.fileImported = true;
      FileManager.allowWatcher = true;
    }
  }
}
