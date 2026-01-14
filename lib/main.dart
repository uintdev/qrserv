import 'dart:io';
import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter/material.dart';
import 'l10n/generated/app_localizations.dart';
import 'package:window_size/window_size.dart';
import 'package:oktoast/oktoast.dart';
import 'package:share_handler/share_handler.dart';
import 'package:path/path.dart' as Path;
import 'package:permission_handler/permission_handler.dart';
import 'package:filesystem_picker/filesystem_picker.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'theme.dart';
import 'components/filemanager.dart';
import 'components/server.dart';
import 'components/preferences.dart';
import 'views/statemanager.dart';
import 'views/about.dart';
import 'views/settings.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Window information for desktop platforms
  if (StateManager().isDesktop) {
    setWindowTitle('QRServ');
    setWindowMinSize(const Size(650, 1200));
    setWindowMaxSize(.infinite);
  }

  runApp(MaterialApp(home: QRServ(), debugShowCheckedModeBanner: false));
}

class QRServ extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    // Portrait only (ignored on Android 16 or later if smallest width is >= 600dp)
    SystemChrome.setPreferredOrientations([.portraitUp, .portraitDown]);

    SystemChrome.setSystemUIOverlayStyle(
      SystemUiOverlayStyle(
        statusBarColor: Theme.of(context).canvasColor,
        statusBarIconBrightness: .light,
        systemNavigationBarColor: Theme.of(context).canvasColor,
        systemNavigationBarIconBrightness: .light,
      ),
    );

    Preferences().load();

    return OKToast(
      // Toast properties
      position: ToastPosition.bottom,
      textPadding: const .fromLTRB(25, 16, 25, 16),
      backgroundColor: const .fromRGBO(60, 60, 60, 1.0),
      duration: const Duration(milliseconds: 3500),
      textStyle: TextStyle(
        fontFamily: QRSTheme.fontFamily,
        color: const .fromRGBO(255, 255, 255, 1),
        fontSize: 13,
        fontVariations: [FontVariation('wght', 300)],
      ),
      radius: 30,
      child: MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        localeListResolutionCallback: (locales, supportedLocales) {
          for (Locale locale in (locales ?? [])) {
            String langCode = locale.toString();
            langCode = langCode.split('_')[0];
            Locale langCodeLocale = Locale(langCode);
            List<Locale> supportedLanguages = supportedLocales.toList();
            if (supportedLanguages.contains(langCodeLocale)) {
              return Locale(langCode);
            }
          }
          return Locale('en');
        },
        theme: QRSTheme.light(ThemeData.light()),
        darkTheme: QRSTheme.dark(ThemeData.dark()),
        home: PageState(title: 'QRServ'),
        debugShowCheckedModeBanner: false,
      ),
    );
  }
}

enum MenuOption { dialogInfo, pageSettings }

class PageState extends StatefulWidget {
  PageState({Key? key, required this.title}) : super(key: key);

  final String title;

  @override
  _Page createState() => _Page();
}

class _Page extends State<PageState> with WidgetsBindingObserver {
  late StreamSubscription _intentDataStreamSubscription;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);

    initShareListener();
  }

  SharedMedia? media;

  Future<void> initShareListener() async {
    final handler = ShareHandler.instance;
    media = await handler.getInitialSharedMedia();

    handler.sharedMediaStream.listen((SharedMedia media) async {
      if (!mounted) return;
      importShare(media.attachments);
    });
  }

  // Import via share receiver
  void importShare(List<SharedAttachment?>? fileData) async {
    if (fileData == null) return;
    if (fileData.isEmpty) return;

    // Prevent further execution if still loading
    if (_actionButtonLoading) {
      showToast(AppLocalizations.of(context)!.info_pending_fileprocessing);
      return;
    }

    // Update button state
    setState(() {
      _actionButtonLoading = true;
      FileManager.directAccessMode = false;
    });

    Map<String, dynamic> fileSelection = {'files': {}};
    int index = 0;
    for (var file in fileData) {
      if (file == null) continue;
      int fileSize = await File(file.path).length();
      fileSelection['files'].addAll({
        index: {
          'name': Path.basename(file.path),
          'path': file.path,
          'size': fileSize,
        },
      });
      index++;
    }

    try {
      await FileManager().selectFile(context, fileSelection).whenComplete(() {
        setState(() {
          _actionButtonLoading = false;
          _stateView = StateManagerPage();
        });
      });
    } catch (error) {
      showToast(
        AppLocalizations.of(context)!.info_exception_fileselection_fallback +
            error.toString(),
      );
    } finally {
      FileManager.fileImportPending = false;
    }
  }

  @override
  dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _intentDataStreamSubscription.cancel();
    super.dispose();
  }

  Widget _stateView = StateManagerPage();

  bool _actionButtonLoading = false;

  // File selection handling
  void importFile() async {
    // Prevent further execution if still loading
    if (_actionButtonLoading) {
      showToast(AppLocalizations.of(context)!.info_pending_fileprocessing);
      return;
    }

    // Update button state
    setState(() {
      _actionButtonLoading = true;
    });

    Map<String, dynamic> fileSelection = {};

    if (FileManager.directAccessMode) {
      final Permission storagePerm;

      if (!Platform.isAndroid) return;

      final androidInfo = await DeviceInfoPlugin().androidInfo;
      if (androidInfo.version.sdkInt <=
          FileManager().directAccessModeNoMESMaxAPI) {
        storagePerm = Permission.storage;
      } else {
        storagePerm = Permission.manageExternalStorage;
      }

      await storagePerm
          .onDeniedCallback(() {
            FileManager.fileImportPending = false;
          })
          .onGrantedCallback(() {
            FileManager.fileImportPending = false;
          })
          .request();

      final Directory rootPath = Directory(FileManager().directAccessPath);

      String? path = await FilesystemPicker.open(
        context: context,
        rootDirectory: rootPath,
        fsType: FilesystemType.file,
        requestPermission: () async => await storagePerm.request().isGranted,
      );

      // User canceled
      if (path == null) {
        setState(() {
          _actionButtonLoading = false;
          FileManager.fileImportPending = false;
          _stateView = StateManagerPage();
        });
        return;
      }

      fileSelection = {'files': {}};
      final int fileSize = 0;
      fileSelection['files'].addAll({
        0: {'name': Path.basename(path), 'path': path, 'size': fileSize},
      });
    }

    // Prompt file import
    try {
      await FileManager().selectFile(context, fileSelection).whenComplete(() {
        setState(() {
          _actionButtonLoading = false;
          _stateView = StateManagerPage();
        });
      });
    } on PlatformException catch (error) {
      String _exceptionData = error.code;

      switch (_exceptionData) {
        // System denied storage access
        case 'read_external_storage_denied':
          {
            pageTypeCurrent = .permissiondenied;
            setState(() {
              _stateView = StateManager().msgPage(context);
            });
          }
          break;

        // Insufficient storage
        case 'unknown_path':
          {
            pageTypeCurrent = .insufficientstorage;
            await Server().shutdownServer(context);
            setState(() {
              _stateView = StateManager().msgPage(context);
            });
          }
          break;

        // Unknown exception -- inform user
        default:
          {
            showToast(
              AppLocalizations.of(
                    context,
                  )!.info_exception_fileselection_fallback +
                  _exceptionData,
            );
          }
          break;
      }

      // Revert FAB state
      _actionButtonLoading = false;
    } catch (error) {
      showToast(
        AppLocalizations.of(context)!.info_exception_fileselection_fallback +
            error.toString(),
      );
    } finally {
      FileManager.fileImportPending = false;
    }
  }

  // Handle server shutdown via FAB
  void shutdownFAB() async {
    pageTypeCurrent = .landing;
    await Server().shutdownServer(context).whenComplete(() async {
      if (!Server.serverRunning) {
        setState(() {
          _stateView = StateManagerPage();
        });
      } else {
        showToast(AppLocalizations.of(context)!.info_exception_shutdownfailed);
      }
    });
  }

  void infoDialogInvoker(BuildContext context) async {
    await About().aboutDialog(context);
  }

  Future<bool> damEligibility() async {
    bool result = false;

    if (Platform.isAndroid) {
      if (FileManager().isPlayStoreFriendly) {
        final androidInfo = await DeviceInfoPlugin().androidInfo;
        if (androidInfo.version.sdkInt <=
            FileManager().directAccessModeNoMESMaxAPI) {
          // Does not require MES permission on Android 10 or lower
          result = true;
        }
      } else {
        result = true;
      }
    }

    return result;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Theme.of(context).canvasColor,
      extendBody: true,
      extendBodyBehindAppBar: true,
      appBar: PreferredSize(
        preferredSize: const .fromHeight(kToolbarHeight),
        child: Container(
          decoration: BoxDecoration(
            boxShadow: [
              BoxShadow(
                color: Theme.of(context).canvasColor,
                offset: const Offset(0, 3),
                spreadRadius: 25,
                blurRadius: 15,
              ),
            ],
          ),
          child: AppBar(
            elevation: 0,
            titleTextStyle: Theme.of(context).textTheme.titleLarge,
            backgroundColor: Theme.of(context).canvasColor,
            title: Padding(
              padding: const .only(left: 5),
              child: Text(
                widget.title,
                style: const TextStyle(
                  fontVariations: [FontVariation('wght', 700)],
                ),
              ),
            ),
            actions: [
              kDebugMode
                  ? IconButton(
                      onPressed: () {
                        showToast(
                          'App is in debug mode -- ' +
                              'performance is degraded and behavior ' +
                              'may not reflect the release build.',
                        );
                        return;
                      },
                      icon: const Icon(Icons.bug_report_outlined),
                    )
                  : SizedBox(width: 0),
              SizedBox(width: 10),
              IconButton(
                onPressed: () async {
                  final bool damEligible = await damEligibility();
                  if (!damEligible) {
                    showToast(
                      'Direct Access Mode for Android 11 or later is only ' +
                          'available on the GitHub version of the app' +
                          ' -- see the \'about\' dialog',
                    );
                    return;
                  }
                  !FileManager.directAccessMode
                      ? showToast(
                          AppLocalizations.of(context)!.dam_state_enabled,
                        )
                      : showToast(
                          AppLocalizations.of(context)!.dam_state_disabled,
                        );
                  setState(() {
                    FileManager.directAccessMode =
                        !FileManager.directAccessMode;
                  });
                },
                icon: !FileManager.directAccessMode
                    ? const Icon(Icons.sd_card_outlined)
                    : const Icon(Icons.sd_card),
              ),
              SizedBox(width: 10),
              menuButton(context),
              SizedBox(width: 15),
            ],
          ),
        ),
      ),

      // Body
      body: NotificationListener<RebuildNotification>(
        onNotification: (_) {
          setState(() {});
          return true;
        },
        child: Stack(
          alignment: .topCenter,
          children: <Widget>[
            Center(
              child: Column(
                mainAxisAlignment: .center,
                children: <Widget>[_stateView],
              ),
            ),
            // FAB layout
            fabLayout(context),
          ],
        ),
      ),
    );
  }

  Positioned fabLayout(BuildContext context) {
    return Positioned(
      bottom: 65.0,
      left: 35,
      right: 35,
      child: Row(
        mainAxisAlignment: .spaceBetween,
        children: [
          fabShutdown(context),
          fabImport(context),
          fabPlaceholder('blank'),
        ],
      ),
    );
  }

  AnimatedSwitcher fabShutdown(BuildContext context) {
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 150),
      transitionBuilder: (Widget child, Animation<double> animation) =>
          ScaleTransition(child: child, scale: animation),
      child: !Server.serverRunning
          ? fabPlaceholder('shutdown_hidden')
          : FloatingActionButton(
              heroTag: 'shutdown',
              elevation: 3,
              backgroundColor: const .fromRGBO(211, 47, 47, 1),
              shape: RoundedRectangleBorder(borderRadius: .circular(30)),
              onPressed: () {
                if (_actionButtonLoading) {
                  showToast(
                    AppLocalizations.of(
                      context,
                    )!.info_pending_fileprocessing_shutdown,
                  );
                } else if (Server.serverRunning && !Server.serverPoweringDown) {
                  shutdownFAB();
                } else {
                  showToast(
                    AppLocalizations.of(context)!.info_pending_servershutdown,
                  );
                }
              },
              child: Icon(
                Icons.power_settings_new,
                color: const .fromRGBO(255, 255, 255, 0.8),
                size: 22.5,
                semanticLabel: AppLocalizations.of(
                  context,
                )!.fab_shutdownserver_label,
              ),
            ),
    );
  }

  FloatingActionButton fabImport(BuildContext context) {
    return FloatingActionButton(
      heroTag: 'import',
      elevation: 3,
      backgroundColor: Theme.of(context).colorScheme.secondaryContainer,
      foregroundColor: Theme.of(context).colorScheme.secondary,
      shape: RoundedRectangleBorder(borderRadius: .circular(30)),
      onPressed: () async {
        importFile();
      },
      child: AnimatedSwitcher(
        duration: const Duration(milliseconds: 150),
        transitionBuilder: (Widget child, Animation<double> animation) =>
            ScaleTransition(child: child, scale: animation),
        child: _actionButtonLoading
            ? StateManager().loadingIndicator(context)
            : Icon(
                Icons.insert_drive_file,
                color: const .fromRGBO(255, 255, 255, 0.8),
                size: 20.0,
                semanticLabel: AppLocalizations.of(
                  context,
                )!.fab_selectfile_label,
              ),
      ),
    );
  }

  Opacity fabPlaceholder(String heroTagName) {
    return Opacity(
      opacity: 0,
      child: FloatingActionButton(
        heroTag: heroTagName,
        elevation: 3,
        onPressed: () {},
        child: Icon(Icons.check_box_outline_blank, size: 20.0),
      ),
    );
  }

  PopupMenuButton<MenuOption> menuButton(BuildContext context) {
    return PopupMenuButton<MenuOption>(
      tooltip: "",
      onSelected: (MenuOption item) {
        switch (item) {
          case .dialogInfo:
            infoDialogInvoker(context);
            break;

          case .pageSettings:
            Navigator.push(
              context,
              MaterialPageRoute(builder: (context) => SettingsPage()),
            );
            break;
        }
      },
      itemBuilder: (BuildContext context) => <PopupMenuEntry<MenuOption>>[
        PopupMenuItem<MenuOption>(
          value: MenuOption.dialogInfo,
          child: Row(
            children: [
              const Icon(Icons.info_outline),
              SizedBox(width: 10),
              Text(AppLocalizations.of(context)!.about_title),
            ],
          ),
        ),
        PopupMenuItem<MenuOption>(
          value: MenuOption.pageSettings,
          child: Row(
            children: [
              const Icon(Icons.settings),
              SizedBox(width: 10),
              Text(AppLocalizations.of(context)!.settings_title),
            ],
          ),
        ),
      ],
    );
  }
}
