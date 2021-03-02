import 'package:flutter/material.dart';
import 'dart:io';
import 'filepicker.dart';
import 'server.dart';
import 'network.dart';
import 'package:filesize/filesize.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:watcher/watcher.dart';
import 'package:auto_size_text/auto_size_text.dart';
import 'package:flutter_translate/flutter_translate.dart';

class StateManagerPage extends StatefulWidget {
  @override
  StateManager createState() => StateManager();
}

class StateManager extends State<StateManagerPage> {
  bool fileExists = false;
  bool interfaceUpdate = false;

  bool setFileStatus(bool state) {
    setState(() {
      fileExists = state;
    });

    return state;
  }

  // List of platforms considered to be desktop
  bool isDesktop = (Platform.isWindows || Platform.isLinux || Platform.isMacOS);

  @override
  Widget build(BuildContext context) {
    Widget _outputState;
    if (FilePicker.fileImported) {
      _outputState = importedPage();
    } else {
      _outputState = landingPage();
    }
    return _outputState;
  }

  Widget landingPage() {
    return Column(
      children: <Widget>[
        Container(
          constraints: BoxConstraints(maxWidth: 300),
          child: Card(
            color: Color.fromRGBO(34, 34, 34, 1),
            shape: new RoundedRectangleBorder(
              borderRadius: new BorderRadius.circular(15.0),
            ),
            elevation: 1,
            child: Container(
              padding: EdgeInsets.all(40),
              child: Column(
                children: <Widget>[
                  Icon(
                    Icons.insert_drive_file,
                    color: Colors.white,
                    size: 80.0,
                    semanticLabel: translate('page.landing.label'),
                  ),
                  SizedBox(
                    height: 20,
                  ),
                  AutoSizeText(
                    translate('page.landing.msg'),
                    style: TextStyle(fontSize: 15.5),
                    textAlign: TextAlign.center,
                    minFontSize: 14,
                  ),
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }

  // Loading page
  Widget loadingPage() {
    return Column(
      children: <Widget>[
        Card(
          color: Color.fromRGBO(34, 34, 34, 1),
          shape: new RoundedRectangleBorder(
            borderRadius: new BorderRadius.circular(40.0),
          ),
          elevation: 1,
          child: Container(
            padding: EdgeInsets.all(20),
            child: CircularProgressIndicator(
              valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
            ),
          ),
        ),
      ],
    );
  }

  Widget loadingIndicator() {
    return SizedBox(
      width: 30,
      height: 30,
      child: CircularProgressIndicator(
        valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
      ),
    );
  }

  // Error pages
  Widget msgPage(int type) {
    Map _msgInfo;

    // Reset state bypass
    interfaceUpdate = false;

    switch (type) {

      // No network
      case 0:
        {
          _msgInfo = {
            'icon': Icons.signal_wifi_off,
            'label': translate('page.info.noconnection.label'),
            'msg': translate('page.info.noconnection.msg'),
          };
        }
        break;

      // Snapshot error while gathering interface list
      case 1:
        {
          _msgInfo = {
            'icon': Icons.error,
            'label': translate('page.info.snapshoterror.label'),
            'msg': translate('page.info.snapshoterror.msg'),
          };
        }
        break;

      // Selected file was removed
      case 2:
        {
          _msgInfo = {
            'icon': Icons.block,
            'label': translate('page.info.fileremoved.label'),
            'msg': translate('page.info.fileremoved.msg'),
          };
        }
        break;

      // Storage permission declined
      case 3:
        {
          _msgInfo = {
            'icon': Icons.error,
            'label': translate('page.info.permissiondenied.label'),
            'msg': translate('page.info.permissiondenied.msg'),
          };
        }
        break;

      // Port reuse
      case 4:
        {
          _msgInfo = {
            'icon': Icons.error,
            'label': translate('page.info.portinuse.label'),
            'msg': translate('page.info.portinuse.msg'),
          };
        }
        break;

      default:
        {
          _msgInfo = {
            'icon': Icons.error,
            'label': translate('page.info.fallback.label'),
            'msg': translate('page.info.fallback.msg') + type.toString(),
          };
        }
        break;
    }

    return Column(
      children: <Widget>[
        Container(
          constraints: BoxConstraints(maxWidth: 300),
          child: Card(
            color: Color.fromRGBO(34, 34, 34, 1),
            shape: new RoundedRectangleBorder(
              borderRadius: new BorderRadius.circular(15.0),
            ),
            elevation: 1,
            child: Container(
              padding: EdgeInsets.all(40),
              child: Column(
                children: <Widget>[
                  Icon(
                    _msgInfo['icon'],
                    color: Colors.white,
                    size: 80.0,
                    semanticLabel: _msgInfo['label'],
                  ),
                  SizedBox(
                    height: 20,
                  ),
                  AutoSizeText(
                    _msgInfo['msg'],
                    style: TextStyle(fontSize: 15.5),
                    textAlign: TextAlign.center,
                    minFontSize: 14,
                  ),
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }

  // Imported page
  String defaultIP = '';
  String selectedIP = '';

  Widget importedPage() {
    return FutureBuilder<Map<String, dynamic>>(
      future: Network().fetchInterfaces(),
      builder: (context, AsyncSnapshot<Map<String, dynamic>> snapshot) {
        if (snapshot.hasError) {
          return msgPage(1);
        } else if (snapshot.hasData && interfaceUpdate ||
            snapshot.connectionState == ConnectionState.done &&
                snapshot.hasData) {
          // Enable state bypass
          interfaceUpdate = true;
          // File information
          Map<String, dynamic> _fileInfo = FilePicker().readInfo();

          // Human readable file size
          String _sizeHuman = filesize(_fileInfo['length'], 2);
          _sizeHuman = _sizeHuman.replaceAll(
              '.', translate('page.imported.decimalseparator'));
          _sizeHuman = _sizeHuman.replaceAll(
              'KB', translate('page.imported.sizesymbol.kb'));
          _sizeHuman = _sizeHuman.replaceAll(
              'MB', translate('page.imported.sizesymbol.mb'));
          _sizeHuman = _sizeHuman.replaceAll(
              'GB', translate('page.imported.sizesymbol.gb'));
          _sizeHuman = _sizeHuman.replaceAll(
              'TB', translate('page.imported.sizesymbol.tb'));
          _sizeHuman = _sizeHuman.replaceAll(
              'B', translate('page.imported.sizesymbol.b'));

          // Only update on next full run or if selected IP is gone
          if (!snapshot.data['interfaces'].contains(selectedIP.toString())) {
            // Use empty string if no initial IP address to choose from
            if (snapshot.data['interfaces'].isEmpty) {
              defaultIP = '';
            } else {
              defaultIP = snapshot.data['interfaces'][0];
            }

            // If no interfaces available, return network error page
            if (defaultIP == '') {
              Server().shutdownServer();
              return msgPage(0);
            }

            // Set default IP
            selectedIP = defaultIP;
          }

          // Check if server exception occurred
          if (Server.serverException) {
            Server.serverException = false;
            return msgPage(4);
          }

          String _hostFormatted;

          // Formatting for IPv6
          if (!Network().checkIPV4(selectedIP)) {
            _hostFormatted = '[$selectedIP]';
          } else {
            _hostFormatted = selectedIP;
          }

          String _hostName =
              'http://$_hostFormatted:${snapshot.data['port'].toString()}/';

          fileExists = Server().fileExists(_fileInfo['path']);

          if (!fileExists) {
            return msgPage(2);
          }

          // File monitoring
          try {
            var watcher = DirectoryWatcher(_fileInfo['pathpart']);
            watcher.events.listen((event) {
              // Check if selected file was removed
              if (event.type.toString() == 'remove' &&
                  event.path == _fileInfo['path']) {
                if (!Server().fileExists(_fileInfo['path'])) {
                  setFileStatus(false);
                }
              }
            });
          } on FileSystemException {
            setFileStatus(false);
          } catch (_) {
            setFileStatus(false);
          }

          // Actual import page
          return Column(
            children: <Widget>[
              Card(
                color: Color.fromRGBO(34, 34, 34, 1),
                shape: new RoundedRectangleBorder(
                  borderRadius: new BorderRadius.circular(15),
                ),
                elevation: 1,
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(15),
                  child: QrImage(
                    data: _hostName,
                    version: QrVersions.auto,
                    size: (MediaQuery.of(context).size.height * .23),
                    backgroundColor: Color.fromRGBO(255, 255, 255, 1.0),
                    padding: EdgeInsets.all(
                        (MediaQuery.of(context).size.height * .029)),
                  ),
                ),
              ),
              SizedBox(height: 25),
              Text(
                _hostName,
                style: TextStyle(fontSize: 15),
              ),
              SizedBox(height: 25),
              ConstrainedBox(
                constraints: const BoxConstraints(
                  maxWidth: 330,
                ),
                child: Card(
                  color: Color.fromRGBO(34, 34, 34, 1),
                  shape: new RoundedRectangleBorder(
                    borderRadius: new BorderRadius.circular(15.0),
                  ),
                  elevation: 1,
                  child: Container(
                    padding: EdgeInsets.all(30),
                    child: Column(
                      children: <Widget>[
                        Card(
                          color: Color.fromRGBO(42, 42, 42, 1),
                          shape: new RoundedRectangleBorder(
                            borderRadius: new BorderRadius.circular(10.0),
                          ),
                          elevation: 2,
                          child: Container(
                            padding: EdgeInsets.fromLTRB(16, 0, 16, 0),
                            child: DropdownButton<String>(
                              dropdownColor: Color.fromRGBO(58, 58, 58, 1),
                              value: selectedIP,
                              isExpanded: true,
                              elevation: 4,
                              underline: SizedBox(),
                              onChanged: (String newValue) {
                                setState(() {
                                  selectedIP = newValue ?? '';
                                });
                              },
                              style: TextStyle(color: Colors.white),
                              items: snapshot.data['interfaces']
                                  .map<DropdownMenuItem<String>>(
                                      (String value) {
                                return DropdownMenuItem<String>(
                                  value: value,
                                  child: Center(
                                    child: Text(value),
                                  ),
                                );
                              }).toList(),
                            ),
                          ),
                        ),
                        SizedBox(
                          height: 20,
                        ),
                        Table(
                          defaultVerticalAlignment:
                              TableCellVerticalAlignment.middle,
                          columnWidths: {
                            0: FlexColumnWidth(2.3),
                            1: FlexColumnWidth(4),
                          },
                          children: [
                            TableRow(
                              children: [
                                Container(
                                  padding:
                                      EdgeInsets.only(right: 10, bottom: 10),
                                  child: Center(
                                    child: Text(
                                      translate('page.imported.file'),
                                      style: TextStyle(fontSize: 14),
                                    ),
                                  ),
                                ),
                                Container(
                                  padding:
                                      EdgeInsets.only(left: 10, bottom: 10),
                                  child: Tooltip(
                                      message: _fileInfo['name'],
                                      showDuration: Duration(seconds: 5),
                                      padding: EdgeInsets.all(10),
                                      textStyle: TextStyle(
                                        fontSize: 14,
                                        color: Colors.black,
                                      ),
                                      child: Center(
                                        child: AutoSizeText(
                                          _fileInfo['name'],
                                          style: TextStyle(fontSize: 14),
                                          minFontSize: 14,
                                          overflow: TextOverflow.ellipsis,
                                          maxLines: 2,
                                        ),
                                      )),
                                ),
                              ],
                            ),
                            TableRow(
                              children: [
                                Container(
                                  padding: EdgeInsets.only(right: 10),
                                  child: Center(
                                    child: Text(
                                      translate('page.imported.size'),
                                      style: TextStyle(fontSize: 14),
                                    ),
                                  ),
                                ),
                                Container(
                                  padding: EdgeInsets.only(left: 10),
                                  child: Center(
                                    child: AutoSizeText(
                                      _sizeHuman,
                                      style: TextStyle(fontSize: 14),
                                      minFontSize: 14,
                                      overflow: TextOverflow.ellipsis,
                                      maxLines: 1,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ],
          );
        } else {
          return loadingPage();
        }
      },
    );
  }
}
