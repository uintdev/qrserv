import 'dart:io';
import 'dart:convert';
import 'package:path_provider/path_provider.dart';

class Preferences {
  // Private constructor to prevent instantiation
  Preferences._();

  static Map<String, dynamic> _preferenceData = {};
  static String _preferenceFileName = 'config.json';

  static Future<void> write(String key, dynamic value) async {
    if (value == null) {
      _preferenceData.remove(key);
    } else {
      _preferenceData[key] = value;
    }
    await _dataWrite();
  }

  static Future<dynamic> read(String key) async {
    return _preferenceData[key];
  }

  static Future<void> load() async {
    final directory = await _preferencesPath();
    final file = File('$directory/$_preferenceFileName');

    if (await file.exists()) {
      final jsonString = await file.readAsString();
      _preferenceData = json.decode(jsonString) as Map<String, dynamic>;
    } else {
      await file.create(recursive: true);
      await file.writeAsString(json.encode(_preferenceData), flush: true);
    }
  }

  static Future<void> clear([void Function()? propertyDefaults]) async {
    _preferenceData.clear();
    propertyDefaults?.call();
    await _dataWrite();
  }

  static Future<void> _dataWrite() async {
    final directory = await _preferencesPath();
    final file = File('${directory}/${_preferenceFileName}');
    await file.create(recursive: true);
    await file.writeAsString(json.encode(_preferenceData), flush: true);
  }

  static Future<String> _preferencesPath() async {
    Directory pathDataRoot = await getApplicationSupportDirectory();
    pathDataRoot = pathDataRoot.parent;
    return '${pathDataRoot.path}/shared_prefs';
  }

  // Property list
  static const PREF_SERVER_PORT = 'server_port';
  static const PREF_CLIENT_DAM = 'client_dam';
  static const PREF_CLIENT_FIU = 'client_fiu';
}
