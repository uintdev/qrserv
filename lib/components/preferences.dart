import 'dart:io';
import 'dart:convert';
import 'package:path_provider/path_provider.dart';

class Preferences {
  static Map<String, dynamic> _preferenceData = {};
  String _preferenceFileName = 'config.json';

  Future<void> write(String key, dynamic value) async {
    if (_preferenceData.containsKey(key) && _preferenceData[key] == null) {
      _preferenceData.remove(key);
    } else {
      _preferenceData[key] = value;
    }
    await _dataWrite();
  }

  Future<dynamic> read(String key) async {
    dynamic value = null;
    if (_preferenceData.containsKey(key)) {
      value = _preferenceData[key];
    }
    return value;
  }

  Future<void> load() async {
    final directory = await _preferencesPath();
    final file = File('${directory}/${_preferenceFileName}');

    final fileExists = await file.exists();

    if (fileExists) {
      final jsonString = await file.readAsString();
      final jsonData = json.decode(jsonString) as Map<String, dynamic>;
      _preferenceData = jsonData;
    } else {
      await file.create(recursive: true);
      await file.writeAsString(json.encode(_preferenceData), flush: true);
    }
  }

  Future<void> clear() async {
    _preferenceData.clear();
    await _dataWrite();
  }

  Future<void> _dataWrite() async {
    final directory = await _preferencesPath();
    final file = File('${directory}/${_preferenceFileName}');
    await file.create(recursive: true);
    await file.writeAsString(json.encode(_preferenceData), flush: true);
  }

  Future<String> _preferencesPath() async {
    Directory pathDataRoot = await getTemporaryDirectory();
    pathDataRoot = pathDataRoot.parent;
    String preferenceDir = pathDataRoot.path + '/shared_prefs';
    return preferenceDir;
  }

  // Property list
  static const PREF_SERVER_PORT = 'server_port';
}
