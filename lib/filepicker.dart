import 'package:file_picker_cross/file_picker_cross.dart';
import 'package:path/path.dart';
import 'cachemanager.dart';
import 'server.dart';

class FilePicker {
  static String _currentFile = '';
  static String _currentFullPath = '';
  static String _currentPath = '';
  static int _currentLength = 0;

  static bool fileImported = false;

  Map<String, dynamic> readInfo() {
    return {
      'name': _currentFile,
      'path': _currentFullPath,
      'pathpart': _currentPath,
      'length': _currentLength
    };
  }

  Future selectFile() async {
    await FilePickerCross.importFromStorage().then((file) {
      // Cache handling
      if (_currentFile != '' &&
          _currentFile != file.fileName &&
          Server().fileExists(_currentFile)) {
        CacheManager().deleteCache(_currentFile);
      }

      // Set file information
      FilePicker._currentFile = file.fileName;
      FilePicker._currentFullPath = file.path;
      FilePicker._currentPath = dirname(file.path);
      FilePicker._currentLength = file.length;

      // Set import status
      FilePicker.fileImported = true;
    });
  }
}
