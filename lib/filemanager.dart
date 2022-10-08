import 'dart:io';
import 'package:path_provider/path_provider.dart';
import 'package:flutter/material.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:filesize/filesize.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter_archive/flutter_archive.dart';
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

  Future selectFile(BuildContext context,
      [Map<String, dynamic> fileSelection = const {}]) async {
    Map<String, dynamic> result = {'files': {}};

    String cacheDir = await FileManager().filePickerPath();
    Directory sourceDir = Directory(cacheDir);

    // Ensure file picker cache directory exists first
    bool dirExists = await sourceDir.exists();
    if (!dirExists) {
      Directory sourceDirCreated = await sourceDir.create();
      sourceDir = sourceDirCreated;
    }

    if (fileSelection.length == 0) {
      FilePickerResult? resultFilePicker = await FilePicker.platform
          .pickFiles(allowMultiple: allowMultipleFiles);

      if (resultFilePicker != null) {
        // File picker handler
        for (int i = 0; i < resultFilePicker.files.length; i++) {
          result['files'].addAll({
            i: {
              'name': resultFilePicker.files[i].name,
              'path': resultFilePicker.files[i].path,
              'size': resultFilePicker.files[i].size,
            }
          });
        }
      }
    } else {
      // Share sheet handler
      // Move files selected via share sheet into usual directory for archiving
      for (int i = 0; i < fileSelection['files'].length; i++) {
        File fileRename = File(fileSelection['files'][i]['path']);
        File fileRenamed = await fileRename
            .rename(cacheDir + '/' + fileSelection['files'][i]['name']);
        fileSelection['files'][i]['path'] = fileRenamed.path;
      }
      result = fileSelection;
    }

    // Only perform file processing if at least one file is selected
    if (result.containsKey('files') && result['files'].length > 0) {
      FileManager.allowWatcher = false;

      multipleFiles = (result['files'].length > 1);

      List<String> cacheExceptionList = [];
      for (int i = 0; i < result['files'].length; i++) {
        archivedFiles.add({
          'file': result['files'][i]['name'],
          'size': result['files'][i]['size']
        });
        cacheExceptionList.add(result['files'][i]['path'] ?? '');
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

        List<File> files = [];

        for (int i = 0; i < result['files'].length; i++) {
          await File(result['files'][i]['path'] ??
                  fullPickerPath + '/' + result['files'][i]['name'])
              .exists()
              .then((_) async {
            files.add(File(result['files'][i]['path'] ??
                fullPickerPath + '/' + result['files'][i]['name']));
          });
        }
        final zipFile = File(fullArchivePath);
        try {
          await ZipFile.createFromFiles(
                  sourceDir: sourceDir, files: files, zipFile: zipFile)
              .then((_) async => {
                    for (int i = 0; i < result['files'].length; i++)
                      {
                        await File(result['files'][i]['path'] ??
                                fullPickerPath +
                                    '/' +
                                    result['files'][i]['name'])
                            .delete()
                      }
                  });
        } catch (e) {
          showToast(AppLocalizations.of(context)!.page_imported_archive_failed +
              e.toString());
          return;
        }

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
        _currentFile = result['files'][0]['name'];
        _currentFullPath = result['files'][0]['path'] ?? '';
        _currentPath = dirname(result['files'][0]['path'] ?? '');
        _currentLength = result['files'][0]['size'];
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
