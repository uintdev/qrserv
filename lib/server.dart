import 'dart:io';
import 'dart:math';
import 'filepicker.dart';
import 'network.dart';
import 'package:oktoast/oktoast.dart';
import 'package:flutter_translate/flutter_translate.dart';

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
  String _tokenGenerator() {
    String _characters =
        '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ';
    String _generateStore = '';

    final _random = new Random();
    for (int i = 0; i < 32; i++) {
      _generateStore += _characters[_random.nextInt(_characters.length - 1)];
    }

    return _generateStore;
  }

  // Web server
  Future http() async {
    await HttpServer.bind(InternetAddress.anyIPv6, 0).then((server) {
      // Update server status
      serverRunning = true;
      // Update port
      Network.port = server.port;
      // Set unique token
      _serverToken = _tokenGenerator();

      server.listen((HttpRequest request) async {
        final token = request.uri.queryParameters['token'] ?? '';
        final response = request.response;
        final fileInfo = FilePicker().readInfo();
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
          // File exists, serve it
          response.statusCode = HttpStatus.ok;
          response.headers
              .add(HttpHeaders.contentLengthHeader, fileInfo['length']);
          response.headers
              .add(HttpHeaders.contentTypeHeader, 'application/octet-stream');
          response.headers.add('Content-Disposition',
              'filename="${Uri.encodeComponent(fileInfo['name'])}"');
          try {
            await response.addStream(targetFile.openRead());
          } catch (_) {
            serverRunning = false;
          }
        } else {
          // File does not exist
          response.statusCode = HttpStatus.notFound;
          serverRunning = false;
        }
        response.close();

        // Shutdown server on error
        if (!serverRunning) server.close();
      });
    });
  }

  // Shutdown server
  Future shutdownServer() async {
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
        showToast(translate('server.info.tokenmismatch.msg'));
      } else {
        // Unhandled HTTP code (misc)
        showToast(translate('server.info.shutdownfailed.msg') +
            response.statusCode.toString());
      }
      serverPoweringDown = false;
    }).onError((error, _) {
      // Server not found, so probably already gone
      showToast(translate('server.info.gone.msg') + error.toString());
      serverRunning = false;
      serverPoweringDown = false;
    });
  }
}
