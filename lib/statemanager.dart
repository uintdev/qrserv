import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:watcher/watcher.dart';
import 'package:auto_size_text/auto_size_text.dart';
import 'package:oktoast/oktoast.dart';
import 'cachemanager.dart';
import 'filemanager.dart';
import 'server.dart';
import 'network.dart';
import 'sharemanager.dart';

enum PageType {
  landing,
  imported,
  noconnection,
  snapshoterror,
  fileremoved,
  permissiondenied,
  insufficientstorage,
  portinuse,
  fallback
}

PageType pageTypeCurrent = PageType.landing;

class RebuildNotification extends Notification {}

class StateManagerPage extends StatefulWidget {
  @override
  StateManager createState() => StateManager();
}

class StateManager extends State<StateManagerPage> {
  bool fileExists = false;
  bool interfaceUpdate = false;

  bool setFileStatus(bool state) {
    if (mounted) {
      setState(() {
        fileExists = state;
      });
    }
    return state;
  }

  // List of platforms considered to be desktop
  final bool isDesktop =
      (Platform.isWindows || Platform.isLinux || Platform.isMacOS);

  @override
  Widget build(BuildContext context) {
    Widget _outputState;
    if (FileManager.fileImportPending) {
      _outputState = loadingPage();
    } else if (pageTypeCurrent == PageType.imported) {
      _outputState = importedPage(context);
    } else {
      _outputState = msgPage(context);
    }
    return _outputState;
  }

  // Loading view
  Widget loadingPage() {
    return Column(
      children: <Widget>[
        Card(
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(40),
          ),
          elevation: 1,
          child: Container(
            padding: const EdgeInsets.all(20),
            child: CircularProgressIndicator(
              valueColor:
                  AlwaysStoppedAnimation<Color>(Theme.of(context).primaryColor),
            ),
          ),
        ),
      ],
    );
  }

  Widget loadingIndicator(BuildContext context) {
    return SizedBox(
      width: 30,
      height: 30,
      child: CircularProgressIndicator(
        valueColor: AlwaysStoppedAnimation<Color>(
          Theme.of(context).colorScheme.secondary,
        ),
      ),
    );
  }

  // Page view
  Widget msgPage(BuildContext context) {
    Map _msgInfo;

    // Reset state bypass
    interfaceUpdate = false;

    PageType pageState = pageTypeCurrent;

    switch (pageState) {
      // Landing
      case PageType.landing:
        {
          _msgInfo = {
            'icon': Icons.insert_drive_file,
            'label': AppLocalizations.of(context)!.page_landing_label,
            'msg': AppLocalizations.of(context)!.page_landing_msg,
          };
        }
        break;

      // No network
      case PageType.noconnection:
        {
          _msgInfo = {
            'icon': Icons.signal_wifi_off,
            'label': AppLocalizations.of(context)!.page_info_noconnection_label,
            'msg': AppLocalizations.of(context)!.page_info_noconnection_msg,
          };
        }
        break;

      // Snapshot error while gathering interface list
      case PageType.snapshoterror:
        {
          _msgInfo = {
            'icon': Icons.error,
            'label':
                AppLocalizations.of(context)!.page_info_snapshoterror_label,
            'msg': AppLocalizations.of(context)!.page_info_snapshoterror_msg,
          };
        }
        break;

      // Selected file was removed
      case PageType.fileremoved:
        {
          _msgInfo = {
            'icon': Icons.block,
            'label': AppLocalizations.of(context)!.page_info_fileremoved_label,
            'msg': AppLocalizations.of(context)!.page_info_fileremoved_msg,
          };
        }
        break;

      // Storage permission declined
      case PageType.permissiondenied:
        {
          _msgInfo = {
            'icon': Icons.error,
            'label':
                AppLocalizations.of(context)!.page_info_permissiondenied_label,
            'msg': AppLocalizations.of(context)!.page_info_permissiondenied_msg,
          };
        }
        break;

      // Insufficient storage
      case PageType.insufficientstorage:
        {
          _msgInfo = {
            'icon': Icons.disc_full,
            'label': AppLocalizations.of(context)!
                .page_info_insufficientstorage_label,
            'msg':
                AppLocalizations.of(context)!.page_info_insufficientstorage_msg,
          };
        }
        break;

      // Port reuse
      case PageType.portinuse:
        {
          _msgInfo = {
            'icon': Icons.error,
            'label': AppLocalizations.of(context)!.page_info_portinuse_label,
            'msg': AppLocalizations.of(context)!.page_info_portinuse_msg,
          };
        }
        break;

      default:
        {
          _msgInfo = {
            'icon': Icons.error,
            'label': AppLocalizations.of(context)!.page_info_fallback_label,
            'msg': AppLocalizations.of(context)!.page_info_fallback_msg +
                pageState.toString(),
          };
        }
        break;
    }

    CacheManager().deleteCache(context);

    return Column(
      children: <Widget>[
        Container(
          constraints: const BoxConstraints(maxWidth: 300),
          child: Card(
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(25),
            ),
            elevation: 1,
            child: Container(
              padding: const EdgeInsets.all(40),
              child: Column(
                children: <Widget>[
                  Icon(
                    _msgInfo['icon'],
                    size: 80,
                    semanticLabel: _msgInfo['label'],
                  ),
                  const SizedBox(height: 20),
                  AutoSizeText(
                    _msgInfo['msg'],
                    style: const TextStyle(fontSize: 13),
                    textAlign: TextAlign.center,
                    minFontSize: 11,
                  ),
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }

  // Imported view
  String defaultIP = '';
  String? selectedIP = '';
  bool fileInPath = false;

  Widget importedPage(BuildContext context) {
    return FutureBuilder<Map<String, dynamic>>(
      future: Network().fetchInterfaces(context),
      builder: (context, AsyncSnapshot<Map<String, dynamic>> snapshot) {
        if (snapshot.hasError) {
          pageTypeCurrent = PageType.snapshoterror;
          return msgPage(context);
        } else if (snapshot.hasData && interfaceUpdate ||
            snapshot.connectionState == ConnectionState.done &&
                snapshot.hasData) {
          // Enable state bypass
          interfaceUpdate = true;
          // File information
          Map<String, dynamic> _fileInfo = FileManager().readInfo();

          // Human readable file size
          String _sizeHuman =
              FileManager().fileSizeHuman(_fileInfo['length'], 2, context);

          // Only update on next full run or if selected IP is gone
          if (!snapshot.data!['interfaces'].contains(selectedIP.toString())) {
            // Use empty string if no initial IP address to choose from
            if (snapshot.data!['interfaces'].isEmpty) {
              defaultIP = '';
            } else {
              defaultIP = snapshot.data!['interfaces'][0];
            }

            // If no interfaces available, return network error page
            if (defaultIP == '') {
              pageTypeCurrent = PageType.noconnection;
              Server().shutdownServer(context);
              return msgPage(context);
            }

            // Set default IP
            selectedIP = defaultIP;
          }

          // Check if server exception occurred
          if (Server.serverException) {
            Server.serverException = false;
            pageTypeCurrent = PageType.portinuse;
            return msgPage(context);
          }

          String? _hostFormatted;
          String _filePath;

          // Formatting for IPv6
          if (!Network().checkIPV4(selectedIP)) {
            _hostFormatted = '[$selectedIP]';
          } else {
            _hostFormatted = selectedIP;
          }

          // Check if to include file name in path
          if (fileInPath) {
            _filePath = Uri.encodeComponent(_fileInfo['name']);
          } else {
            _filePath = '';
          }

          String _hostName =
              'http://$_hostFormatted:${snapshot.data!['port'].toString()}/$_filePath';

          fileExists = Server().fileExists(_fileInfo['path']);

          if (!fileExists) {
            pageTypeCurrent = PageType.fileremoved;
            return msgPage(context);
          }

          // File monitoring
          try {
            DirectoryWatcher watcher = DirectoryWatcher(_fileInfo['pathpart']);
            watcher.events.listen((event) {
              // Check if selected file was removed
              if (event.type.toString() == 'remove' &&
                  event.path == _fileInfo['path'] &&
                  FileManager.allowWatcher) {
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

          String fileDataTip() {
            String fileResult = '';
            List archivedFile = [];
            if (!isDesktop) {
              List archivedList = FileManager().readInfo()['archived'];

              if (archivedList.length > 0) {
                archivedFile.add(_fileInfo['name']);

                archivedList.forEach((element) {
                  archivedFile.add(element['file'] +
                      ' (' +
                      FileManager().fileSizeHuman(element['size'], 2, context) +
                      ')');
                });

                fileResult = archivedFile.join('\n');
              } else {
                fileResult = _fileInfo['name'];
              }
            } else {
              fileResult = _fileInfo['path'];
            }
            return fileResult;
          }

          // Import layout
          return importedFileView(
              _hostName, context, snapshot, fileDataTip, _fileInfo, _sizeHuman);
        } else {
          return loadingPage();
        }
      },
    );
  }

  Column importedFileView(
      String _hostName,
      BuildContext context,
      AsyncSnapshot<Map<String, dynamic>> snapshot,
      String fileDataTip(),
      Map<String, dynamic> _fileInfo,
      String _sizeHuman) {
    return Column(
      children: <Widget>[
        importedFileQR(_hostName, context),
        const SizedBox(height: 30),
        ConstrainedBox(
          constraints: const BoxConstraints(
            maxWidth: 330,
          ),
          child: Card(
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(25),
            ),
            elevation: 1,
            child: Container(
              padding: const EdgeInsets.all(30),
              child: Column(
                children: <Widget>[
                  Table(
                    defaultVerticalAlignment: TableCellVerticalAlignment.middle,
                    columnWidths: {
                      0: const FlexColumnWidth(15),
                      1: const FlexColumnWidth(1),
                      2: const FlexColumnWidth(3.8),
                    },
                    children: [
                      TableRow(
                        children: [
                          importedFileInterfaces(context, snapshot),
                          const SizedBox(width: 20),
                          importedFileShare(_hostName, context),
                        ],
                      ),
                    ],
                  ),
                  const SizedBox(height: 20),
                  importedFileInfo(
                      context, fileDataTip, _fileInfo, _sizeHuman, snapshot),
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }

  Card importedFileQR(String _hostName, BuildContext context) {
    return Card(
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(25),
      ),
      elevation: 1,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(25),
        child: QrImageView(
          data: _hostName,
          version: QrVersions.auto,
          size: (MediaQuery.of(context).size.height * .23),
          backgroundColor: const Color.fromRGBO(255, 255, 255, 1),
          padding: EdgeInsets.all((MediaQuery.of(context).size.height * .029)),
        ),
      ),
    );
  }

  SizedBox importedFileShare(String _hostName, BuildContext context) {
    return SizedBox(
      width: 48,
      height: 48,
      child: TextButton(
        style: ElevatedButton.styleFrom(
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(10),
          ),
          backgroundColor: const Color.fromRGBO(47, 45, 54, 1),
          elevation: 2,
          shadowColor: Colors.black,
        ),
        onPressed: () {
          ShareManager().share(_hostName, context);
        },
        onLongPress: () {
          !fileInPath
              ? showToast(AppLocalizations.of(context)!
                  .page_imported_fileinpath_enabled)
              : showToast(AppLocalizations.of(context)!
                  .page_imported_fileinpath_disabled);
          setState(() {
            fileInPath = !fileInPath;
          });
        },
        child: Icon(
          !isDesktop ? Icons.share : Icons.copy,
          size: 17,
          color: Theme.of(context).primaryColor,
          semanticLabel: !isDesktop
              ? AppLocalizations.of(context)!.page_imported_share_sheet_label
              : AppLocalizations.of(context)!
                  .page_imported_share_clipboard_label,
        ),
      ),
    );
  }

  Card importedFileInterfaces(
      BuildContext context, AsyncSnapshot<Map<String, dynamic>> snapshot) {
    return Card(
      color: Theme.of(context).canvasColor,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(10),
      ),
      elevation: 2,
      child: ButtonTheme(
        alignedDropdown: true,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(10),
        ),
        child: DropdownButton<String>(
          icon: Row(
            children: const [
              Icon(Icons.arrow_drop_down),
              SizedBox(width: 10),
            ],
          ),
          borderRadius: BorderRadius.circular(10),
          dropdownColor: Theme.of(context).canvasColor,
          value: selectedIP,
          isExpanded: true,
          elevation: 4,
          underline: const SizedBox(),
          onChanged: (String? newValue) {
            setState(() {
              selectedIP = newValue;
            });
          },
          style: Theme.of(context).textTheme.bodyMedium,
          items: snapshot.data!['interfaces']
              .map<DropdownMenuItem<String>>((String value) {
            return DropdownMenuItem<String>(
              value: value,
              child: Center(
                child: Text(value),
              ),
            );
          }).toList(),
        ),
      ),
    );
  }
}

Table importedFileInfo(
    BuildContext context,
    String fileDataTip(),
    Map<String, dynamic> _fileInfo,
    String _sizeHuman,
    AsyncSnapshot<Map<String, dynamic>> snapshot) {
  return Table(
    defaultVerticalAlignment: TableCellVerticalAlignment.middle,
    columnWidths: {
      0: const FlexColumnWidth(2.3),
      1: const FlexColumnWidth(4),
    },
    children: [
      TableRow(
        children: [
          Container(
            padding: const EdgeInsets.only(right: 10),
            child: Center(
              child: Text(
                AppLocalizations.of(context)!.page_imported_file,
                style: const TextStyle(fontSize: 13),
              ),
            ),
          ),
          Container(
            padding: const EdgeInsets.only(left: 10),
            child: Tooltip(
              message: fileDataTip(),
              showDuration: const Duration(seconds: 5),
              padding: const EdgeInsets.all(10),
              child: Center(
                child: AutoSizeText(
                  _fileInfo['name'],
                  style: const TextStyle(fontSize: 13),
                  minFontSize: 11,
                  overflow: TextOverflow.ellipsis,
                  maxLines: 2,
                ),
              ),
            ),
          ),
        ],
      ),
      TableRow(
        children: [
          Container(
            padding: const EdgeInsets.only(right: 10),
            child: Center(
              child: Text(
                AppLocalizations.of(context)!.page_imported_size,
                style: const TextStyle(fontSize: 13),
              ),
            ),
          ),
          Container(
            padding: const EdgeInsets.only(left: 10),
            child: Center(
              child: Text(
                _sizeHuman,
                style: const TextStyle(fontSize: 13),
              ),
            ),
          ),
        ],
      ),
      TableRow(
        children: [
          Container(
            padding: const EdgeInsets.only(right: 10),
            child: Center(
              child: Text(
                AppLocalizations.of(context)!.page_imported_port,
                style: const TextStyle(fontSize: 13),
              ),
            ),
          ),
          Container(
            padding: const EdgeInsets.only(left: 10),
            child: Center(
              child: Text(
                snapshot.data!['port'].toString(),
                style: const TextStyle(fontSize: 13),
              ),
            ),
          ),
        ],
      ),
    ],
  );
}
