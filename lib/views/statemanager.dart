import 'dart:async';
import 'dart:io';
import 'dart:ui' as UI;
import 'package:flutter/material.dart';
import '../l10n/generated/app_localizations.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:watcher/watcher.dart';
import 'package:oktoast/oktoast.dart';
import '../theme.dart';
import '../components/cache.dart';
import '../components/filemanager.dart';
import '../components/server.dart';
import '../components/network.dart';
import '../components/share.dart';

enum PageType {
  landing,
  imported,
  noconnection,
  snapshoterror,
  fileremoved,
  filemodified,
  permissiondenied,
  insufficientstorage,
  portinuse,
  fallback,
}

PageType pageTypeCurrent = .landing;

class RebuildNotification extends Notification {}

class StateManagerPage extends StatefulWidget {
  @override
  StateManager createState() => StateManager();
}

class StateManager extends State<StateManagerPage> {
  bool fileUntampered = false;
  static PageType fileTampered = .fileremoved;
  bool interfaceUpdate = false;
  static StreamSubscription<WatchEvent>? importWatchdog;
  DirectoryWatcher? watcher;

  bool setFileStatus(bool state, [PageType stateType = .fileremoved]) {
    if (mounted) {
      setState(() {
        fileUntampered = state;
        fileTampered = stateType;
      });
    }
    return state;
  }

  // List of platforms considered to be desktop
  final bool isDesktop =
      (Platform.isWindows || Platform.isLinux || Platform.isMacOS);

  // Cancel watcher subscription on server shutdown
  void watcherUnsubscriber() {
    if (!FileManager.allowWatcher) {
      if (importWatchdog != null && importWatchdog?.cancel != null) {
        importWatchdog?.cancel();
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    Widget _outputState;

    watcherUnsubscriber();

    if (FileManager.fileImportPending) {
      _outputState = loadingPage();
    } else if (pageTypeCurrent == .imported) {
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
          shape: RoundedRectangleBorder(borderRadius: .circular(40)),
          elevation: 1,
          child: Container(
            padding: const .all(20),
            child: CircularProgressIndicator(
              valueColor: AlwaysStoppedAnimation<Color>(
                Theme.of(context).primaryColor,
              ),
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
      case .landing:
        {
          _msgInfo = {
            'icon': Icons.insert_drive_file,
            'label': AppLocalizations.of(context)!.page_landing_label,
            'msg': AppLocalizations.of(context)!.page_landing_msg,
          };
        }
        break;

      // No network
      case .noconnection:
        {
          _msgInfo = {
            'icon': Icons.signal_wifi_off,
            'label': AppLocalizations.of(context)!.page_info_noconnection_label,
            'msg': AppLocalizations.of(context)!.page_info_noconnection_msg,
          };
        }
        break;

      // Snapshot error while gathering interface list
      case .snapshoterror:
        {
          _msgInfo = {
            'icon': Icons.error,
            'label': AppLocalizations.of(
              context,
            )!.page_info_snapshoterror_label,
            'msg': AppLocalizations.of(context)!.page_info_snapshoterror_msg,
          };
        }
        break;

      // Selected file was removed
      case .fileremoved:
        {
          _msgInfo = {
            'icon': Icons.block,
            'label': AppLocalizations.of(context)!.page_info_fileremoved_label,
            'msg': AppLocalizations.of(context)!.page_info_fileremoved_msg,
          };
        }
        break;

      // Selected file was modified
      case .filemodified:
        {
          _msgInfo = {
            'icon': Icons.edit,
            'label': AppLocalizations.of(context)!.page_info_filemodified_label,
            'msg': AppLocalizations.of(context)!.page_info_filemodified_msg,
          };
        }
        break;

      // Storage permission declined
      case .permissiondenied:
        {
          _msgInfo = {
            'icon': Icons.error,
            'label': AppLocalizations.of(
              context,
            )!.page_info_permissiondenied_label,
            'msg': AppLocalizations.of(context)!.page_info_permissiondenied_msg,
          };
        }
        break;

      // Insufficient storage
      case .insufficientstorage:
        {
          _msgInfo = {
            'icon': Icons.disc_full,
            'label': AppLocalizations.of(
              context,
            )!.page_info_insufficientstorage_label,
            'msg': AppLocalizations.of(
              context,
            )!.page_info_insufficientstorage_msg,
          };
        }
        break;

      // Port reuse
      case .portinuse:
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
            'msg':
                AppLocalizations.of(context)!.page_info_fallback_msg +
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
            shape: RoundedRectangleBorder(borderRadius: .circular(25)),
            elevation: 1,
            child: Container(
              padding: const .all(40),
              child: Column(
                children: <Widget>[
                  Icon(
                    _msgInfo['icon'],
                    size: 80,
                    semanticLabel: _msgInfo['label'],
                  ),
                  const SizedBox(height: 20),
                  Text(
                    _msgInfo['msg'],
                    style: const TextStyle(
                      fontSize: 14,
                      fontVariations: [FontVariation('wght', 400)],
                    ),
                    textAlign: .center,
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
          pageTypeCurrent = .snapshoterror;
          return msgPage(context);
        } else if (snapshot.hasData && interfaceUpdate ||
            snapshot.connectionState == ConnectionState.done &&
                snapshot.hasData) {
          // Enable state bypass
          interfaceUpdate = true;
          // File information
          Map<String, dynamic> _fileInfo = FileManager().readInfo();

          // Human readable file size
          String _sizeHuman = FileManager().fileSizeHuman(
            _fileInfo['length'],
            context,
          );

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
              pageTypeCurrent = .noconnection;
              Server().shutdownServer(context);
              return msgPage(context);
            }

            // Set default IP
            selectedIP = defaultIP;
          }

          // Check if server exception occurred
          if (Server.serverException) {
            Server.serverException = false;
            pageTypeCurrent = .portinuse;
            return msgPage(context);
          }

          String? _hostFormatted;
          String _filePath;

          // Formatting for IPv6
          if (!Network().checkIPv4(selectedIP)) {
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

          fileUntampered =
              (Server().fileExists(_fileInfo['path']) &&
              !(fileTampered == .filemodified));

          if (!fileUntampered) {
            pageTypeCurrent = fileTampered;
            return msgPage(context);
          }

          // File monitoring
          try {
            if (!FileManager.lockWatcher) {
              FileManager.lockWatcher = true;
              watcherUnsubscriber();
              FileWatcher watcher = FileWatcher(_fileInfo['path']);
              importWatchdog = watcher.events.listen((event) {
                if (!(event.path == _fileInfo['path'] &&
                    FileManager.allowWatcher))
                  return;

                bool watchedFileExists = Server().fileExists(_fileInfo['path']);

                if (!watchedFileExists) {
                  setFileStatus(false);
                } else if (event.type == .MODIFY &&
                    watchedFileExists &&
                    FileManager().directModeDetect(_fileInfo['path'])) {
                  setFileStatus(false, .filemodified);
                }
              });
            }
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
                  archivedFile.add(
                    element['file'] +
                        ' (' +
                        FileManager().fileSizeHuman(element['size'], context) +
                        ')',
                  );
                });

                fileResult = archivedFile.join('\n');
              } else {
                if (FileManager().directModeDetect(_fileInfo['path'])) {
                  fileResult = _fileInfo['path'];
                } else {
                  fileResult = _fileInfo['name'];
                }
              }
            } else {
              fileResult = _fileInfo['path'];
            }
            return fileResult;
          }

          // Import layout
          return importedFileView(
            _hostName,
            context,
            snapshot,
            fileDataTip,
            _fileInfo,
            _sizeHuman,
          );
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
    String _sizeHuman,
  ) {
    return Column(
      children: <Widget>[
        importedFileQR(_hostName, context),
        const SizedBox(height: 30),
        ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 330),
          child: Card(
            shape: RoundedRectangleBorder(borderRadius: .circular(25)),
            elevation: 1,
            child: Container(
              padding: const .all(30),
              child: Column(
                children: [
                  importedFileInfoName(
                    context,
                    fileDataTip,
                    _fileInfo,
                    _sizeHuman,
                  ),
                  const SizedBox(height: 5),
                  Row(
                    mainAxisSize: .min,
                    children: [
                      SizedBox(
                        width: 196,
                        child: importedFileInterfaces(context, snapshot),
                      ),
                      const SizedBox(width: 5),
                      SizedBox(
                        width: 60,
                        child: importedFileShare(_hostName, context),
                      ),
                    ],
                  ),
                  const SizedBox(height: 15),
                  importedFileInfo(
                    context,
                    fileDataTip,
                    _fileInfo,
                    _sizeHuman,
                    snapshot,
                  ),
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
      shape: RoundedRectangleBorder(borderRadius: .circular(25)),
      elevation: 1,
      clipBehavior: .antiAlias,
      child: GestureDetector(
        onLongPress: () {
          ShareManager.copyURL(_hostName, context);
        },
        child: ForceLTR(
          Tooltip(
            message: _hostName,
            triggerMode: .tap,
            showDuration: Duration(days: 1),
            padding: const .all(10),
            textStyle: TextStyle(
              fontFamily: QRSTheme.fontFamily,
              color: Theme.of(context).canvasColor,
              fontSize: 13,
              fontVariations: [FontVariation('wght', 500)],
            ),
            child: QrImageView(
              data: _hostName,
              size: (MediaQuery.of(context).size.height * .23),
              backgroundColor: const .fromRGBO(255, 255, 255, 1),
              padding: .all((MediaQuery.of(context).size.height * .029)),
            ),
          ),
        ),
      ),
    );
  }

  Card importedFileShare(String _hostName, BuildContext context) {
    return Card(
      color: Theme.of(context).canvasColor,
      shape: RoundedRectangleBorder(borderRadius: .circular(10)),
      elevation: 2,
      child: SizedBox(
        height: 48,
        child: TextButton(
          style: ElevatedButton.styleFrom(
            shape: RoundedRectangleBorder(borderRadius: .circular(10)),
          ),
          onPressed: () {
            ShareManager().share(_hostName, context);
          },
          onLongPress: () {
            !fileInPath
                ? showToast(
                    AppLocalizations.of(
                      context,
                    )!.page_imported_fileinpath_enabled,
                  )
                : showToast(
                    AppLocalizations.of(
                      context,
                    )!.page_imported_fileinpath_disabled,
                  );
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
                : AppLocalizations.of(
                    context,
                  )!.page_imported_share_clipboard_label,
          ),
        ),
      ),
    );
  }

  Card importedFileInterfaces(
    BuildContext context,
    AsyncSnapshot<Map<String, dynamic>> snapshot,
  ) {
    return Card(
      color: Theme.of(context).canvasColor,
      shape: RoundedRectangleBorder(borderRadius: .circular(10)),
      elevation: 2,
      child: ForceLTR(
        ButtonTheme(
          alignedDropdown: true,
          shape: RoundedRectangleBorder(borderRadius: .circular(10)),
          child: DropdownButton<String>(
            icon: Row(
              children: const [
                Icon(Icons.arrow_drop_down),
                SizedBox(width: 10),
              ],
            ),
            borderRadius: .circular(10),
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
            items: snapshot.data!['interfaces'].map<DropdownMenuItem<String>>((
              String value,
            ) {
              return DropdownMenuItem<String>(
                value: value,
                child: Center(
                  child: Text(
                    value,
                    style: const TextStyle(
                      fontSize: 12,
                      fontVariations: [FontVariation('wght', 300)],
                    ),
                    overflow: .ellipsis,
                    maxLines: 2,
                    textAlign: .center,
                  ),
                ),
              );
            }).toList(),
          ),
        ),
      ),
    );
  }
}

Widget importedFileInfoName(
  BuildContext context,
  String fileDataTip(),
  Map<String, dynamic> _fileInfo,
  String _sizeHuman,
) {
  return ForceLTR(
    Tooltip(
      message: fileDataTip(),
      showDuration: const Duration(seconds: 5),
      padding: const .all(10),
      textStyle: TextStyle(
        fontFamily: QRSTheme.fontFamily,
        color: Theme.of(context).canvasColor,
        fontSize: 13,
        fontVariations: [FontVariation('wght', 500)],
      ),
      child: Card(
        color: Theme.of(context).canvasColor,
        shape: RoundedRectangleBorder(borderRadius: .circular(10)),
        elevation: 2,
        child: Padding(
          padding: const .fromLTRB(20, 18, 22, 18),
          child: Row(
            children: [
              Icon(importedFileInfoIcon(_fileInfo['name']), size: 16),
              SizedBox(width: 15),
              Flexible(
                fit: FlexFit.tight,
                child: Text(
                  _fileInfo['name'],
                  style: const TextStyle(
                    fontSize: 13,
                    fontVariations: [FontVariation('wght', 300)],
                  ),
                  overflow: .ellipsis,
                  maxLines: 1,
                  textAlign: .center,
                ),
              ),
            ],
          ),
        ),
      ),
    ),
  );
}

Column importedFileInfo(
  BuildContext context,
  String fileDataTip(),
  Map<String, dynamic> _fileInfo,
  String _sizeHuman,
  AsyncSnapshot<Map<String, dynamic>> snapshot,
) {
  const double tableGap = 4;
  return Column(
    children: [
      Table(
        defaultVerticalAlignment: .middle,
        columnWidths: {
          0: const FlexColumnWidth(2.3),
          1: const FlexColumnWidth(3),
        },
        children: [
          TableRow(
            children: [
              Container(
                padding: const .only(right: 10),
                child: Center(
                  child: Text(
                    AppLocalizations.of(context)!.page_imported_size,
                    style: const TextStyle(
                      fontSize: 13,
                      fontVariations: [FontVariation('wght', 600)],
                    ),
                    textAlign: .left,
                  ),
                ),
              ),
              Container(
                padding: const .only(left: 10),
                child: Center(
                  child: ForceLTR(
                    Text(
                      _sizeHuman,
                      style: const TextStyle(
                        fontSize: 13,
                        fontVariations: [FontVariation('wght', 300)],
                      ),
                      textAlign: .right,
                    ),
                  ),
                ),
              ),
            ],
          ),
          TableRow(
            children: [
              Container(
                padding: const .only(right: 10, top: tableGap),
                child: Center(
                  child: Text(
                    AppLocalizations.of(context)!.page_imported_port,
                    style: const TextStyle(
                      fontSize: 13,
                      fontVariations: [FontVariation('wght', 600)],
                    ),
                    textAlign: .left,
                  ),
                ),
              ),
              Container(
                padding: const .only(left: 10, top: tableGap),
                child: Center(
                  child: ForceLTR(
                    Text(
                      snapshot.data!['port'].toString(),
                      style: const TextStyle(
                        fontSize: 13,
                        fontVariations: [FontVariation('wght', 300)],
                      ),
                      textAlign: .right,
                    ),
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    ],
  );
}

IconData importedFileInfoIcon(String fileName) {
  IconData result = Icons.insert_drive_file;

  final int dotIndex = fileName.lastIndexOf('.');

  if (dotIndex == -1) return result;

  final String fileExtension = (fileName.substring(dotIndex + 1)).toLowerCase();

  final List<String> fileExtensionsArchive = [
    '7z',
    'xz',
    'bz2',
    'gz',
    'tar',
    'zip',
    'rar',
    'cab',
  ];
  final List<String> fileExtensionsImage = [
    'png',
    'jpg',
    'jpeg',
    'webp',
    'avif',
    'bmp',
    'gif',
    'heic',
    'heif',
    'svg',
    'tif',
    'tiff',
  ];
  final List<String> fileExtensionsVideo = [
    '3gp',
    'avi',
    'mkv',
    'mov',
    'mp4',
    'mpeg',
    'mpg',
    'webm',
    'wmv',
  ];
  final List<String> fileExtensionsAudio = [
    'aac',
    'aiff',
    'flac',
    'm3a',
    'mp4',
    'mid',
    'midi',
    'mka',
    'mp3',
    'ogg',
    'wav',
    'weba',
    'wma',
  ];

  if (fileExtensionsArchive.contains(fileExtension)) {
    result = Icons.folder_zip;
  }

  if (fileExtensionsImage.contains(fileExtension)) {
    result = Icons.image;
  }

  if (fileExtensionsVideo.contains(fileExtension)) {
    result = Icons.video_file;
  }

  if (fileExtensionsAudio.contains(fileExtension)) {
    result = Icons.audio_file;
  }

  if (fileExtension == 'apk') {
    result = Icons.android;
  }

  return result;
}

Widget ForceLTR(Widget child) {
  return Directionality(textDirection: UI.TextDirection.ltr, child: child);
}
