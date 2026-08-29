import 'dart:io';
import 'package:path_provider/path_provider.dart';
import 'package:material_ui/material_ui.dart';
import '../l10n/generated/app_localizations.dart';
import 'package:filesize/filesize.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter_archive/flutter_archive.dart';
import 'package:path/path.dart';
import 'package:oktoast/oktoast.dart';
import 'cache.dart';
import 'server.dart';
import 'network.dart';
import '../views/statemanager.dart';

class FileManager {
  // Private constructor to prevent instantiation
  FileManager._();

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
  static final int directAccessModeNoMESMaxAPI = 29;
  static final String directAccessPath = '/storage/emulated/0';

  /*
  This determines if DAM is allowed on Android 11 or later in the build.
  Use '--dart-define=NO_DAM=true' in the build command to disable Direct Access Mode
  */
  static const bool isPlayStoreBuild = bool.fromEnvironment(
    'NO_DAM',
    defaultValue: false,
  );

  static Map<String, dynamic> readInfo() {
    return {
      'name': currentFile,
      'path': currentFullPath,
      'pathpart': currentPath,
      'length': currentLength,
      'archived': archivedFiles,
    };
  }

  static Future<String> filePickerPath(bool ignoreDAM) async {
    return (!ignoreDAM && FileManager.directAccessMode)
        ? directAccessPath
        : (await getTemporaryDirectory()).path + '/file_picker';
  }

  static bool directModeDetect(String path) {
    return path.startsWith(directAccessPath);
  }

  static String fileSizeHuman(int length, BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    String sizeHuman = filesize(length, 2);
    sizeHuman = sizeHuman
        .replaceAll('TB', l10n.page_imported_sizesymbol_tb)
        .replaceAll('GB', l10n.page_imported_sizesymbol_gb)
        .replaceAll('MB', l10n.page_imported_sizesymbol_mb)
        .replaceAll('KB', l10n.page_imported_sizesymbol_kb)
        .replaceAll(' B', ' ' + l10n.page_imported_sizesymbol_b)
        .replaceAll('.', l10n.page_imported_decimalseparator);

    return sizeHuman;
  }

  static Future selectFile(
    BuildContext context, [
    Map<String, dynamic> fileSelection = const {},
    bool ignoreDAM = false,
  ]) async {
    FileManager.fileImportPending = true;
    Map<String, dynamic> result = {'files': {}};

    final String pickerDir = await FileManager.filePickerPath(ignoreDAM);
    Directory sourceDir = Directory(pickerDir);

    // Ensure file picker directory exists first
    final bool dirExists = await sourceDir.exists();
    if (!dirExists) {
      if (directAccessMode) {
        showToast(AppLocalizations.of(context)!.dam_path_not_found);
        return;
      }
      sourceDir = await sourceDir.create();
    }

    if (!directAccessMode && fileSelection.isEmpty) {
      // Default file picker
      final List<PlatformFile> resultFilePicker = await FilePicker.pickFiles();

      if (resultFilePicker.isNotEmpty) {
        // File picker handler
        int fileIndex = 0;
        for (final file in resultFilePicker) {
          if (file.path == null) continue;

          final File fileToMove = File(file.path!);
          String fileToMoveName = file.name;

          // Check if selection has multiple files of the same name
          for (int j = 0; j < result['files'].length; j++) {
            if (result['files'][j]['name'] == fileToMoveName) {
              fileToMoveName =
                  Server.tokenGenerator('0123456789abcdef', 6) +
                  '_' +
                  fileToMoveName;
            }
          }

          final String fileToMoveNewPath = '$pickerDir/$fileToMoveName';
          await fileToMove.rename(fileToMoveNewPath);

          result['files'].addAll({
            fileIndex: {
              'name': fileToMoveName,
              'path': fileToMoveNewPath,
              'size': await file.length(),
            },
          });
          fileIndex++;
        }
      }
    } else if (fileSelection.isNotEmpty &&
        directModeDetect(fileSelection['files'][0]['path'])) {
      // Direct access mode
      final File selectedFile = File(fileSelection['files'][0]['path']);

      if (await selectedFile.exists()) {
        fileSelection['files'][0]['size'] = await selectedFile.length();
      } else {
        // File was selected but no longer exists
        pageTypeCurrent = .fileremoved;
        await Server.shutdownServer(context);
        return;
      }
      result = fileSelection;
    } else {
      // Share sheet handler
      // Move files selected via share sheet into usual directory for archiving
      for (int i = 0; i < fileSelection['files'].length; i++) {
        final File fileRename = File(fileSelection['files'][i]['path']);
        final File fileRenamed = await fileRename.rename(
          '$pickerDir/${fileSelection['files'][i]['name']}',
        );
        fileSelection['files'][i]['path'] = fileRenamed.path;
      }
      result = fileSelection;
    }

    if (result.containsKey('files') && result['files'].length == 0) {
      FileManager.fileImportPending = false;
      return;
    }

    await Network.internalIP();
    if (Network.interfaceList.isEmpty) {
      pageTypeCurrent = .noconnection;
      await Server.shutdownServer(context);
      return;
    }

    // Only perform file processing if at least one file is selected
    if (result.containsKey('files') && result['files'].length > 0) {
      FileManager.allowWatcher = false;

      // Clear out existing watcher subscription
      if (StateManager.importWatchdog != null &&
          StateManager.importWatchdog?.cancel != null) {
        await StateManager.importWatchdog?.cancel();
      }

      multipleFiles = result['files'].length > 1;

      final List<String> cacheExceptionList = [];
      archivedFiles = [];
      for (int i = 0; i < result['files'].length; i++) {
        archivedFiles.add({
          'file': result['files'][i]['name'],
          'size': result['files'][i]['size'],
        });
        cacheExceptionList.add(result['files'][i]['path'] ?? '');
      }

      // Empty last archive entry if current selection does not contain multiple files
      if (!multipleFiles) archivedLast = '';

      // Exclude previously created archive (if any) initially
      if (archivedLast.isEmpty) cacheExceptionList.add(archivedLast);

      // Cache handling
      await CacheManager.deleteCache(
        context,
        cacheExceptionList,
        true,
        ignoreDAM,
      );

      String currentFileLocal = '';
      String currentFullPathLocal = '';
      String currentPathLocal = '';
      int currentLengthLocal = 0;

      if (multipleFiles) {
        // Prepare archive name
        final String archiveName =
            Server.tokenGenerator('1234567890ABCDEF', 8) + '.zip';
        final String fullArchivePath = '$pickerDir/$archiveName';

        final List<File> files = [];
        for (int i = 0; i < result['files'].length; i++) {
          final String path =
              result['files'][i]['path'] ??
              '$pickerDir/${result['files'][i]['name']}';
          if (await File(path).exists()) {
            files.add(File(path));
          }
        }

        final File zipFile = File(fullArchivePath);
        try {
          await ZipFile.createFromFiles(
            sourceDir: sourceDir,
            files: files,
            zipFile: zipFile,
          );
          for (final file in files) {
            await file.delete();
          }
        } catch (e) {
          showToast(
            AppLocalizations.of(context)!.page_imported_archive_failed +
                e.toString(),
          );
          return;
        }

        // Get length of created archive
        final int archiveSize = await File(fullArchivePath).length();

        currentFileLocal = archiveName;
        currentFullPathLocal = fullArchivePath;
        currentPathLocal = pickerDir;
        currentLengthLocal = archiveSize;
        archivedLast = currentFullPathLocal;
      } else {
        currentFileLocal = result['files'][0]['name'];
        currentFullPathLocal = result['files'][0]['path'] ?? '';
        currentPathLocal = dirname(result['files'][0]['path'] ?? '');
        currentLengthLocal = result['files'][0]['size'];
        archivedFiles = [];
        archivedLast = '';
      }

      // Set file information
      FileManager.currentFile = currentFileLocal;
      FileManager.currentFullPath = currentFullPathLocal;
      FileManager.currentPath = currentPathLocal;
      FileManager.currentLength = currentLengthLocal;
      FileManager.fileImported = true;
      FileManager.allowWatcher = true;
      FileManager.lockWatcher = false;
      StateManager.fileTampered = .fileremoved;
      pageTypeCurrent = .imported;

      // Initiate server
      await Network.fetchInterfaces(context);
    }
    FileManager.fileImportPending = false;
  }
}
