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
import 'network.dart';
import 'statemanager.dart';

class FileManager {
  static String currentFile = '';
  static String currentFullPath = '';
  static String currentPath = '';
  static int currentLength = 0;
  static List archivedFiles = [];
  static String archivedLast = '';

  static bool fileImported = false;
  static bool fileImportPending = false;
  static bool multipleFiles = false;
  static bool allowWatcher = false;
  static bool lockWatcher = false;
  static bool directAccessMode = false;
  final String directAccessPath = '/storage/emulated/0';
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
    String pickerDir = FileManager.directAccessMode
        ? directAccessPath
        : (await getTemporaryDirectory()).path + '/file_picker';
    return pickerDir;
  }

  bool directModeDetect(String path) {
    bool result = false;

    if (path.startsWith(directAccessPath)) {
      result = true;
    }

    return result;
  }

  String fileSizeHuman(int length, BuildContext context) {
    String _sizeHuman = filesize(length, 2);
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
    FileManager.fileImportPending = true;
    Map<String, dynamic> result = {'files': {}};

    String pickerDir = await FileManager().filePickerPath();
    Directory sourceDir = Directory(pickerDir);

    // Ensure file picker directory exists first
    bool dirExists = await sourceDir.exists();
    if (!dirExists) {
      if (directAccessMode) {
        // TODO: i19n message
        showToast('Path for direct access mode not found');
        return;
      }
      Directory sourceDirCreated = await sourceDir.create();
      sourceDir = sourceDirCreated;
    }

    if (!directAccessMode && fileSelection.length == 0) {
      // Default file picker
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
    } else if (fileSelection.length > 0 &&
        directModeDetect(fileSelection['files'][0]['path'])) {
      // Direct access mode
      final File selectedFile = File(fileSelection['files'][0]['path']);

      if (selectedFile.existsSync()) {
        fileSelection['files'][0]['size'] = selectedFile.lengthSync();
      } else {
        // File was selected but no longer exists
        pageTypeCurrent = PageType.fileremoved;
        await Server().shutdownServer(context);
        return;
      }
      result = fileSelection;
    } else {
      // Share sheet handler
      // Move files selected via share sheet into usual directory for archiving
      for (int i = 0; i < fileSelection['files'].length; i++) {
        File fileRename = File(fileSelection['files'][i]['path']);
        File fileRenamed = await fileRename
            .rename(pickerDir + '/' + fileSelection['files'][i]['name']);
        fileSelection['files'][i]['path'] = fileRenamed.path;
      }
      result = fileSelection;
    }

    if (result.containsKey('files') && result['files'].length == 0) {
      FileManager.fileImportPending = false;
      return;
    }

    await Network().internalIP();
    if (Network.interfaceList.isEmpty) {
      pageTypeCurrent = PageType.noconnection;
      await Server().shutdownServer(context);
      return;
    }

    // Only perform file processing if at least one file is selected
    if (result.containsKey('files') && result['files'].length > 0) {
      FileManager.allowWatcher = false;

      // Clear out existing watcher subscription
      if (!FileManager.allowWatcher) {
        if (StateManager.importWatchdog != null &&
            StateManager.importWatchdog?.cancel != null) {
          await StateManager.importWatchdog?.cancel();
        }
      }

      multipleFiles = (result['files'].length > 1);

      List<String> cacheExceptionList = [];
      archivedFiles = [];
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
      if (archivedLast.isEmpty) cacheExceptionList.add(archivedLast);

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
      FileManager.lockWatcher = false;
      pageTypeCurrent = PageType.imported;

      // Initiate server
      await Network().fetchInterfaces(context);
    }
    FileManager.fileImportPending = false;
  }
}
