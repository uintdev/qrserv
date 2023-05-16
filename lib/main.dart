import 'dart:io';
import 'package:flutter/services.dart';
import 'package:flutter/material.dart';
import 'dart:async';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:window_size/window_size.dart';
import 'package:flutter_statusbarcolor_ns/flutter_statusbarcolor_ns.dart';
import 'package:oktoast/oktoast.dart';
import 'package:receive_sharing_intent/receive_sharing_intent.dart';
import 'package:path/path.dart' as Path;
import 'theme.dart';
import 'filemanager.dart';
import 'statemanager.dart';
import 'server.dart';
import 'info.dart';
import 'package:in_app_update/in_app_update.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Window information for desktop platforms
  if (StateManager().isDesktop) {
    setWindowTitle('QRServ');
    setWindowMinSize(const Size(650, 1200));
    setWindowMaxSize(Size.infinite);
  }

  runApp(MaterialApp(
    home: QRServ(),
    debugShowCheckedModeBanner: false,
  ));
}

class QRServ extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    // Portrait only
    SystemChrome.setPreferredOrientations([
      DeviceOrientation.portraitUp,
      DeviceOrientation.portraitDown,
    ]);

    return OKToast(
      // Toast properties
      position: ToastPosition.bottom,
      textPadding: const EdgeInsets.fromLTRB(25, 16, 25, 16),
      backgroundColor: const Color.fromRGBO(60, 60, 60, 1.0),
      duration: const Duration(milliseconds: 3500),
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
        theme: QRSTheme.light(ThemeData.light(useMaterial3: true)),
        darkTheme: QRSTheme.dark(ThemeData.dark(useMaterial3: true)),
        home: PageState(title: 'QRServ'),
        debugShowCheckedModeBanner: false,
      ),
    );
  }
}

class PageState extends StatefulWidget {
  PageState({Key? key, required this.title}) : super(key: key);

  final String title;

  @override
  _Page createState() => _Page();
}

class _Page extends State<PageState> with WidgetsBindingObserver {
  // Bar themes
  bool _useWhiteStatusBarForeground = false;
  bool _useWhiteNavigationBarForeground = false;

  late StreamSubscription _intentDataStreamSubscription;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);

    // Import via share receiver
    void importShare(List fileData) async {
      if (fileData.isEmpty) return;

      // Prevent further execution if still loading
      if (_actionButtonLoading) {
        showToast(AppLocalizations.of(context)!.info_pending_fileprocessing);
        return;
      }

      // Update button state
      setState(() {
        _actionButtonLoading = true;
      });

      Map<String, dynamic> fileSelection = {'files': {}};
      int index = 0;
      for (var file in fileData) {
        int fileSize = File(file.path).lengthSync();
        fileSelection['files'].addAll({
          index: {
            'name': Path.basename(file.path),
            'path': file.path,
            'size': fileSize,
          }
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
        showToast(AppLocalizations.of(context)!
                .info_exception_fileselection_fallback +
            error.toString());
      } finally {
        FileManager.fileImportPending = false;
      }
    }

    if (!StateManager().isDesktop) {
      // Intent share receiver (when in memory)
      _intentDataStreamSubscription = ReceiveSharingIntent.getMediaStream()
          .listen((List<SharedMediaFile> value) async {
        importShare(value);
      }, onError: (err) {
        showToast(
            AppLocalizations.of(context)!.info_exception_intentstream + err);
      });

      // Intent share receiver (when closed)
      ReceiveSharingIntent.getInitialMedia().then(
          (List<SharedMediaFile> value) async {
        importShare(value);
      }, onError: (err) {
        showToast(
            AppLocalizations.of(context)!.info_exception_intentstream + err);
      });
    }
  }

  @override
  dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _intentDataStreamSubscription.cancel();
    super.dispose();
  }

  @override
  didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      if (_useWhiteStatusBarForeground)
        FlutterStatusbarcolor.setStatusBarWhiteForeground(
            _useWhiteStatusBarForeground);
      if (_useWhiteNavigationBarForeground)
        FlutterStatusbarcolor.setNavigationBarWhiteForeground(
            _useWhiteNavigationBarForeground);
    }
    super.didChangeAppLifecycleState(state);
  }

  void changeStatusColor(Color color) async {
    try {
      await FlutterStatusbarcolor.setStatusBarColor(color);
      if (useWhiteForeground(color)) {
        FlutterStatusbarcolor.setStatusBarWhiteForeground(true);
        FlutterStatusbarcolor.setNavigationBarWhiteForeground(true);
        _useWhiteStatusBarForeground = true;
        _useWhiteNavigationBarForeground = true;
      } else {
        FlutterStatusbarcolor.setStatusBarWhiteForeground(false);
        FlutterStatusbarcolor.setNavigationBarWhiteForeground(false);
        _useWhiteStatusBarForeground = false;
        _useWhiteNavigationBarForeground = false;
      }
    } on PlatformException catch (e) {
      showToast(AppLocalizations.of(context)!.info_exception_statusbar +
          e.toString());
    }
  }

  void changeNavigationColor(Color color) async {
    try {
      await FlutterStatusbarcolor.setNavigationBarColor(color);
    } on PlatformException catch (e) {
      showToast(AppLocalizations.of(context)!.info_exception_navigationbar +
          e.toString());
    }
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

    // Prompt file import
    try {
      await FileManager().selectFile(context).whenComplete(() {
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
            pageTypeCurrent = PageType.permissiondenied;
            setState(() {
              _stateView = StateManager().msgPage(context);
            });
          }
          break;

        // Insufficient storage
        case 'unknown_path':
          {
            pageTypeCurrent = PageType.insufficientstorage;
            await Server().shutdownServer(context);
            setState(() {
              _stateView = StateManager().msgPage(context);
            });
          }
          break;

        // Unknown exception -- inform user
        default:
          {
            showToast(AppLocalizations.of(context)!
                    .info_exception_fileselection_fallback +
                _exceptionData);
          }
          break;
      }

      // Revert FAB state
      _actionButtonLoading = false;
    } catch (error) {
      showToast(
          AppLocalizations.of(context)!.info_exception_fileselection_fallback +
              error.toString());
    } finally {
      FileManager.fileImportPending = false;
    }
  }

  // Handle server shutdown via FAB
  void shutdownFAB() async {
    pageTypeCurrent = PageType.landing;
    await Server().shutdownServer(context).whenComplete(() async {
      if (!Server.serverRunning) {
        setState(() {
          FileManager.fileImported = false;
          _stateView = StateManagerPage();
        });
      } else {
        showToast(AppLocalizations.of(context)!.info_exception_shutdownfailed);
      }
    });
  }

  void infoDialogInvoker(BuildContext context) async {
    await Info().infoDialog(context);
  }

  Future<void> checkForUpdate() async {
    if (!Platform.isAndroid) return;

    InAppUpdate.checkForUpdate().then((info) async {
      if (info.updateAvailability == UpdateAvailability.updateAvailable) {
        await InAppUpdate.performImmediateUpdate().onError((error, _) {
          showToast(error.toString());
          return AppUpdateResult.inAppUpdateFailed;
        });
      }
    }).onError((error, _) {
      // TODO: potentially handle ERROR_API_NOT_AVAILABLE
      // Surprisingly did not experience that.. yet.
      showToast(error.toString());
    });
  }

  bool initialLaunch = true;

  @override
  Widget build(BuildContext context) {
    // Code to run on initial launch
    if (initialLaunch) {
      initialLaunch = false;
      checkForUpdate();
    }

    if (!StateManager().isDesktop) {
      // Apply system UI colours
      changeStatusColor(Theme.of(context).canvasColor);
      changeNavigationColor(Theme.of(context).canvasColor);
    }

    return Scaffold(
      backgroundColor: Theme.of(context).canvasColor,
      extendBody: true,
      extendBodyBehindAppBar: true,
      appBar: PreferredSize(
        preferredSize: const Size.fromHeight(kToolbarHeight),
        child: Container(
          decoration: BoxDecoration(boxShadow: [
            BoxShadow(
              color: Theme.of(context).canvasColor,
              offset: const Offset(0, 3),
              spreadRadius: 25,
              blurRadius: 15,
            )
          ]),
          child: AppBar(
            elevation: 0,
            titleTextStyle: Theme.of(context).textTheme.titleLarge,
            backgroundColor: Theme.of(context).canvasColor,
            title: Padding(
              padding: const EdgeInsets.only(left: 5),
              child: Text(widget.title),
            ),
            actions: [
              IconButton(
                onPressed: () {
                  infoDialogInvoker(context);
                },
                icon: Icon(Icons.info_outline),
              ),
              SizedBox(width: 15)
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
          alignment: Alignment.topCenter,
          children: <Widget>[
            Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: <Widget>[
                  _stateView,
                ],
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
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          fabShutdown(context),
          fabImport(context),
        ],
      ),
    );
  }

  AnimatedSwitcher fabShutdown(BuildContext context) {
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 250),
      transitionBuilder: (Widget child, Animation<double> animation) =>
          ScaleTransition(child: child, scale: animation),
      child: !Server.serverRunning
          ? SizedBox()
          : FloatingActionButton(
              elevation: 3,
              backgroundColor: Colors.red.shade700,
              foregroundColor: Colors.red.shade100,
              onPressed: () {
                if (_actionButtonLoading) {
                  showToast(AppLocalizations.of(context)!
                      .info_pending_fileprocessing_shutdown);
                } else if (Server.serverRunning && !Server.serverPoweringDown) {
                  shutdownFAB();
                } else {
                  showToast(AppLocalizations.of(context)!
                      .info_pending_servershutdown);
                }
              },
              child: Icon(
                Icons.power_settings_new,
                color: Colors.red.shade100,
                size: 22.5,
                semanticLabel:
                    AppLocalizations.of(context)!.fab_shutdownserver_label,
              ),
            ),
    );
  }

  FloatingActionButton fabImport(BuildContext context) {
    return FloatingActionButton(
      elevation: 3,
      backgroundColor: Theme.of(context).colorScheme.secondaryContainer,
      foregroundColor: Theme.of(context).colorScheme.secondary,
      onPressed: () {
        importFile();
      },
      child: AnimatedSwitcher(
        duration: const Duration(milliseconds: 250),
        transitionBuilder: (Widget child, Animation<double> animation) =>
            ScaleTransition(child: child, scale: animation),
        child: _actionButtonLoading
            ? StateManager().loadingIndicator(context)
            : Icon(
                Icons.insert_drive_file,
                color: Theme.of(context).colorScheme.secondary,
                size: 20.0,
                semanticLabel:
                    AppLocalizations.of(context)!.fab_selectfile_label,
              ),
      ),
    );
  }
}
