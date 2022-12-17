import 'dart:io';
import 'dart:math';
import 'package:flutter/material.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:oktoast/oktoast.dart';
import 'cachemanager.dart';
import 'filemanager.dart';
import 'network.dart';

class Server {
  // Check if specified file path exists
  bool fileExists([String file = '']) {
    bool _filePresent = false;
    if (file == '') return _filePresent;

    File _filePath = new File(file);
    if (_filePath.existsSync()) {
      _filePresent = true;
    }

    return _filePresent;
  }

  // Server states
  static bool serverRunning = false;
  static bool serverException = false;
  static bool serverPoweringDown = false;

  // Server token
  static String _serverToken = '';

  // Create unique token
  String tokenGenerator({String characters = '', int length = 32}) {
    String _characters = '';
    if (characters == '') {
      _characters =
          '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ';
    } else {
      _characters = characters;
    }
    String _generateStore = '';

    final _random = new Random();
    for (int i = 0; i < length; i++) {
      _generateStore += _characters[_random.nextInt(_characters.length - 1)];
    }

    return _generateStore;
  }

  // Web server
  Future http(context) async {
    await HttpServer.bind(InternetAddress.anyIPv6, 0).then((server) {
      // Update server status
      serverRunning = true;
      // Update port
      Network.port = server.port;
      // Set unique token
      _serverToken = tokenGenerator();

      server.listen((HttpRequest request) async {
        final token = request.uri.queryParameters['token'] ?? '';
        final response = request.response;
        final fileInfo = FileManager().readInfo();
        File targetFile = File(fileInfo['path']);

        if (token != '') {
          if (token == _serverToken) {
            // If provided generated token matches then shutdown server
            response.statusCode = HttpStatus.accepted;
            serverRunning = false;
          } else {
            response.statusCode = HttpStatus.unauthorized;
          }
        } else if (await targetFile.exists()) {
          // File exists, prepare response headers
          response.statusCode = HttpStatus.ok;
          response.headers
              .add(HttpHeaders.contentTypeHeader, 'application/octet-stream');
          response.headers.add('Content-Disposition',
              'filename="${Uri.encodeComponent(fileInfo['name'])}"');
          // Get content length
          RandomAccessFile openedFile = targetFile.openSync();
          response.headers
              .add(HttpHeaders.contentLengthHeader, openedFile.lengthSync());
          openedFile.closeSync();
          // Serve file
          try {
            await response.addStream(targetFile.openRead());
          } catch (error) {
            showToast(AppLocalizations.of(context)!.server_info_gone +
                error.toString());
            serverRunning = false;
          }
        } else {
          // File does not exist
          response.statusCode = HttpStatus.notFound;
          serverRunning = false;
        }
        response.close();

        // Shutdown server
        if (!serverRunning) {
          await server.close();
          serverRunning = false;
          serverPoweringDown = false;
          FileManager.fileImported = false;
          await CacheManager().deleteCache(context);
        }
      });
    });
  }

  // Shutdown server
  Future shutdownServer(BuildContext context) async {
    // Do not proceed if server is not running
    if (!serverRunning) return;

    // Set server power down progress state
    serverPoweringDown = true;

    // Begin request
    HttpClient client = new HttpClient();
    await client
        .getUrl(Uri.parse(
            'http://localhost:${Network.port.toString()}/?token=$_serverToken'))
        .then((HttpClientRequest request) {
      return request.close();
    }).then((HttpClientResponse response) {
      if (response.statusCode == 202) {
        // Server had shutdown successfully
      } else if (response.statusCode == 401) {
        // Provided token did not match
        showToast(AppLocalizations.of(context)!.server_info_tokenmismatch);
      } else {
        // Unhandled HTTP code (misc)
        showToast(AppLocalizations.of(context)!.server_info_shutdownfailed +
            response.statusCode.toString());
      }
      serverPoweringDown = false;
    }).onError((error, _) async {
      // Server not found, so probably already gone
      showToast(
          AppLocalizations.of(context)!.server_info_gone + error.toString());
      serverRunning = false;
      serverPoweringDown = false;
      FileManager.fileImported = false;
      await CacheManager().deleteCache(context);
    });
  }
}
