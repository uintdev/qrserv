import 'dart:async';
import 'package:material_ui/material_ui.dart';
import '../l10n/generated/app_localizations.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:watcher/watcher.dart';
import 'package:path/path.dart' as path;
import '../theme.dart';
import '../components/cache.dart';
import '../components/filemanager.dart';
import '../components/server.dart';
import '../components/network.dart';
import '../components/share.dart';
import 'settings/fiu.dart';

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

  // Cancel watcher subscription on server shutdown
  void watcherUnsubscriber() {
    if (!FileManager.allowWatcher) {
      importWatchdog?.cancel();
    }
  }

  @override
  void dispose() {
    importWatchdog?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    watcherUnsubscriber();

    if (FileManager.fileImportPending) return loadingPage();
    if (pageTypeCurrent == .imported) return importedPage(context);
    return msgPage(context);
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
    // Reset state bypass
    interfaceUpdate = false;

    final Map<String, dynamic> msgInfo = switch (pageTypeCurrent) {
      // Landing
      .landing => {
        'icon': Icons.insert_drive_file,
        'label': AppLocalizations.of(context)!.page_landing_label,
        'msg': AppLocalizations.of(context)!.page_landing_msg,
      },

      // No network
      .noconnection => {
        'icon': Icons.signal_wifi_off,
        'label': AppLocalizations.of(context)!.page_info_noconnection_label,
        'msg': AppLocalizations.of(context)!.page_info_noconnection_msg,
      },

      // Snapshot error while gathering interface list
      .snapshoterror => {
        'icon': Icons.error,
        'label': AppLocalizations.of(context)!.page_info_snapshoterror_label,
        'msg': AppLocalizations.of(context)!.page_info_snapshoterror_msg,
      },

      // Selected file was removed
      .fileremoved => {
        'icon': Icons.block,
        'label': AppLocalizations.of(context)!.page_info_fileremoved_label,
        'msg': AppLocalizations.of(context)!.page_info_fileremoved_msg,
      },

      // Selected file was modified
      .filemodified => {
        'icon': Icons.edit,
        'label': AppLocalizations.of(context)!.page_info_filemodified_label,
        'msg': AppLocalizations.of(context)!.page_info_filemodified_msg,
      },

      // Storage permission declined
      .permissiondenied => {
        'icon': Icons.error,
        'label': AppLocalizations.of(context)!.page_info_permissiondenied_label,
        'msg': AppLocalizations.of(context)!.page_info_permissiondenied_msg,
      },

      // Insufficient storage
      .insufficientstorage => {
        'icon': Icons.disc_full,
        'label': AppLocalizations.of(
          context,
        )!.page_info_insufficientstorage_label,
        'msg': AppLocalizations.of(context)!.page_info_insufficientstorage_msg,
      },

      // Port reuse
      .portinuse => {
        'icon': Icons.error,
        'label': AppLocalizations.of(context)!.page_info_portinuse_label,
        'msg': AppLocalizations.of(context)!.page_info_portinuse_msg,
      },

      _ => {
        'icon': Icons.error,
        'label': AppLocalizations.of(context)!.page_info_fallback_label,
        'msg':
            '${AppLocalizations.of(context)!.page_info_fallback_msg}'
            '$pageTypeCurrent',
      },
    };

    CacheManager.deleteCache(context);

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
                    msgInfo['icon'],
                    size: 80,
                    semanticLabel: msgInfo['label'],
                  ),
                  const SizedBox(height: 20),
                  Text(
                    msgInfo['msg'],
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

  Widget importedPage(BuildContext context) {
    return FutureBuilder<Map<String, dynamic>>(
      future: Network.fetchInterfaces(context),
      builder: (context, AsyncSnapshot<Map<String, dynamic>> snapshot) {
        if (snapshot.hasError) {
          pageTypeCurrent = .snapshoterror;
          return msgPage(context);
        } else if (snapshot.hasData &&
            (interfaceUpdate || snapshot.connectionState == .done)) {
          // Enable state bypass
          interfaceUpdate = true;
          // File information
          final Map<String, dynamic> fileInfo = FileManager.readInfo();

          // Human readable file size
          final String sizeHuman = FileManager.fileSizeHuman(
            fileInfo['length'],
            context,
          );

          // Only update on next full run or if selected IP is gone
          if (!snapshot.data!['interfaces'].contains(selectedIP.toString())) {
            // Use empty string if no initial IP address to choose from
            defaultIP = snapshot.data!['interfaces'].isEmpty
                ? ''
                : snapshot.data!['interfaces'][0];

            // If no interfaces available, return network error page
            if (defaultIP.isEmpty) {
              pageTypeCurrent = .noconnection;
              Server.shutdownServer(context);
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

          // Formatting for IPv6
          final String? hostFormatted = Network.checkIPv4(selectedIP)
              ? selectedIP
              : '[$selectedIP]';

          // Check if to include file name in path
          final String filePath = FIU.state
              ? Uri.encodeComponent(fileInfo['name'])
              : '';

          final String hostName =
              'http://$hostFormatted:${snapshot.data!['port']}/$filePath';

          fileUntampered =
              Server.fileExists(fileInfo['path']) &&
              fileTampered != .filemodified;

          if (!fileUntampered) {
            pageTypeCurrent = fileTampered;
            return msgPage(context);
          }

          // File monitoring
          try {
            if (!FileManager.lockWatcher) {
              FileManager.lockWatcher = true;
              watcherUnsubscriber();
              final FileWatcher fileWatcher = FileWatcher(fileInfo['path']);
              importWatchdog = fileWatcher.events.listen((event) {
                if (event.path != fileInfo['path'] ||
                    !FileManager.allowWatcher) {
                  return;
                }

                final bool watchedFileExists = Server.fileExists(
                  fileInfo['path'],
                );

                if (!watchedFileExists) {
                  setFileStatus(false);
                } else if (event.type == .MODIFY &&
                    watchedFileExists &&
                    FileManager.directModeDetect(fileInfo['path'])) {
                  setFileStatus(false, .filemodified);
                }
              });
            }
          } catch (_) {
            setFileStatus(false);
          }

          String fileDataTip() {
            final List archivedList = FileManager.readInfo()['archived'];

            if (archivedList.isNotEmpty) {
              final List archivedFile = [fileInfo['name']];

              for (final element in archivedList) {
                archivedFile.add(
                  '${element['file']} '
                  '(${FileManager.fileSizeHuman(element['size'], context)})',
                );
              }

              return archivedFile.join('\n');
            }

            return FileManager.directModeDetect(fileInfo['path'])
                ? fileInfo['path']
                : fileInfo['name'];
          }

          // Import layout
          return importedFileView(
            hostName,
            context,
            snapshot,
            fileDataTip,
            fileInfo,
            sizeHuman,
          );
        } else {
          return loadingPage();
        }
      },
    );
  }

  Column importedFileView(
    String hostName,
    BuildContext context,
    AsyncSnapshot<Map<String, dynamic>> snapshot,
    String fileDataTip(),
    Map<String, dynamic> fileInfo,
    String sizeHuman,
  ) {
    return Column(
      children: <Widget>[
        importedFileQR(hostName, context),
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
                    fileInfo,
                    sizeHuman,
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
                        child: importedFileShare(hostName, context),
                      ),
                    ],
                  ),
                  const SizedBox(height: 15),
                  importedFileInfo(
                    context,
                    fileDataTip,
                    fileInfo,
                    sizeHuman,
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

  Card importedFileQR(String hostName, BuildContext context) {
    return Card(
      shape: RoundedRectangleBorder(borderRadius: .circular(25)),
      elevation: 1,
      clipBehavior: .antiAlias,
      child: GestureDetector(
        onLongPress: () {
          ShareManager.copyURL(hostName, context);
        },
        child: ForceLTR(
          Tooltip(
            message: hostName,
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
              data: hostName,
              size: (MediaQuery.of(context).size.height * .23),
              backgroundColor: const .fromRGBO(255, 255, 255, 1),
              padding: .all((MediaQuery.of(context).size.height * .029)),
            ),
          ),
        ),
      ),
    );
  }

  Card importedFileShare(String hostName, BuildContext context) {
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
            ShareManager.shareSheet(hostName);
          },
          child: Icon(
            Icons.share,
            size: 17,
            color: Theme.of(context).primaryColor,
            semanticLabel: AppLocalizations.of(
              context,
            )!.page_imported_share_sheet_label,
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
  Map<String, dynamic> fileInfo,
  String sizeHuman,
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
              Icon(importedFileInfoIcon(fileInfo['name']), size: 16),
              SizedBox(width: 15),
              Flexible(
                fit: FlexFit.tight,
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: <Widget>[
                    Flexible(
                      child: Text(
                        truncateShowFileExtension(fileInfo['name'])
                            ? fileInfo['name'].split(
                                path.extension(fileInfo['name']),
                              )[0]
                            : fileInfo['name'],
                        style: const TextStyle(
                          fontSize: 13,
                          fontVariations: [FontVariation('wght', 300)],
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        textAlign: .center,
                      ),
                    ),
                    Text(
                      truncateShowFileExtension(fileInfo['name'])
                          ? path.extension(fileInfo['name'])
                          : '',
                      style: const TextStyle(
                        fontSize: 13,
                        fontVariations: [FontVariation('wght', 300)],
                      ),
                    ),
                  ],
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
  Map<String, dynamic> fileInfo,
  String sizeHuman,
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
                      sizeHuman,
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
  final int dotIndex = fileName.lastIndexOf('.');

  if (dotIndex == -1) return Icons.insert_drive_file;

  final String fileExtension = fileName.substring(dotIndex + 1).toLowerCase();

  const List<String> fileExtensionsArchive = [
    '7z',
    'xz',
    'bz2',
    'gz',
    'tar',
    'zip',
    'rar',
    'cab',
  ];
  const List<String> fileExtensionsImage = [
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
  const List<String> fileExtensionsVideo = [
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
  const List<String> fileExtensionsAudio = [
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

  if (fileExtensionsArchive.contains(fileExtension)) return Icons.folder_zip;
  if (fileExtensionsImage.contains(fileExtension)) return Icons.image;
  if (fileExtensionsVideo.contains(fileExtension)) return Icons.video_file;
  if (fileExtensionsAudio.contains(fileExtension)) return Icons.audio_file;
  if (fileExtension == 'apk') return Icons.android;

  return Icons.insert_drive_file;
}

Widget ForceLTR(Widget child) {
  return Directionality(textDirection: TextDirection.ltr, child: child);
}

bool truncateShowFileExtension(String fileName) {
  return RegExp(r'^.+\.[A-z]{1,16}$').hasMatch(fileName);
}
