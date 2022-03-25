import 'package:flutter/services.dart';
import 'package:flutter/scheduler.dart';
import 'package:flutter/material.dart';
import 'dart:async';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:window_size/window_size.dart';
import 'package:sliding_up_panel/sliding_up_panel.dart';
import 'package:flutter_statusbarcolor_ns/flutter_statusbarcolor_ns.dart';
import 'package:oktoast/oktoast.dart';
import 'package:receive_sharing_intent/receive_sharing_intent.dart';
import 'theme.dart';
import 'filemanager.dart';
import 'cachemanager.dart';
import 'statemanager.dart';
import 'sharemanager.dart';
import 'server.dart';
import 'panel.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Window information for desktop platforms
  if (StateManager().isDesktop) {
    setWindowTitle('QRServ');
    setWindowMinSize(const Size(650, 1200));
    setWindowMaxSize(Size.infinite);
  }

  runApp(MaterialApp(home: QRServ()));
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
          theme: FlutterDark.dark(ThemeData.dark()),
          home: PageState(title: 'QRServ'),
        ));
  }
}

class PageState extends StatefulWidget {
  PageState({Key? key, required this.title}) : super(key: key);

  final String title;

  @override
  _Page createState() => _Page();
}

class _Page extends State<PageState> with WidgetsBindingObserver {
  // Panel
  final double _initFabHeight = 77.0;
  double _fabHeight = 0;
  double _fabPos = 0;
  double _panelHeightOpen = 0;
  double _panelHeightClosed = 45.0;
  bool _initRun = true;

  // Bar themes
  bool _useWhiteStatusBarForeground = false;
  bool _useWhiteNavigationBarForeground = false;

  late StreamSubscription _intentDataStreamSubscription;

  @override
  void initState() {
    super.initState();

    _fabHeight = _initFabHeight;
    WidgetsBinding.instance.addObserver(this);

    // Import via share receiver
    void importShare(file) async {
      ShareManager().importShared(context, file).whenComplete(() {
        if (FileManager.fileImported) {
          // Update state
          setState(() {
            _stateView = StateManagerPage();
          });
        }
      });
    }

    if (!StateManager().isDesktop) {
      // Intent share receiver (when in memory)
      _intentDataStreamSubscription = ReceiveSharingIntent.getMediaStream()
          .listen((List<SharedMediaFile> value) {
        for (var file in value) {
          importShare(file.path);
          break;
        }

        // Clear cache
        if (value.length > 0) {
          CacheManager()
              .deleteCache(context, FileManager().readInfo()['path'], true);
        } else {
          CacheManager().deleteCache(context);
        }
      }, onError: (err) {
        showToast(
            AppLocalizations.of(context)!.info_exception_intentstream + err);
      });

      // Intent share receiver (when closed)
      ReceiveSharingIntent.getInitialMedia()
          .then((List<SharedMediaFile> value) {
        for (var file in value) {
          importShare(file.path);
          break;
        }

        // Clear cache
        if (value.length > 0) {
          CacheManager()
              .deleteCache(context, FileManager().readInfo()['path'], true);
        } else {
          CacheManager().deleteCache(context);
        }
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

  changeStatusColor(Color color) async {
    try {
      await FlutterStatusbarcolor.setStatusBarColor(color, animate: true);
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

  changeNavigationColor(Color color) async {
    try {
      await FlutterStatusbarcolor.setNavigationBarColor(color, animate: true);
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

    // Attempt file import
    try {
      await FileManager().selectFile(context).whenComplete(() {
        if (FileManager.fileImported) {
          // Update state
          setState(() {
            _stateView = StateManagerPage();
          });
        }
        setState(() {
          _actionButtonLoading = false;
        });
      });
    } on PlatformException catch (error) {
      String _exceptionData = error.code;

      switch (_exceptionData) {

        // System denied storage access
        case 'read_external_storage_denied':
          {
            setState(() {
              _stateView =
                  StateManager().msgPage(PageMsg.permissiondenied, context);
            });
          }
          break;

        // Insufficient storage
        case 'unknown_path':
          {
            Server().shutdownServer(context);
            setState(() {
              _stateView =
                  StateManager().msgPage(PageMsg.insufficientstorage, context);
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
    }
  }

  // Handle server shutdown via FAB
  void shutdownFAB() async {
    await Server().shutdownServer(context).whenComplete(() {
      if (!Server.serverRunning) {
        setState(() {
          FileManager.fileImported = false;
          _stateView = StateManagerPage();
        });
        CacheManager().deleteCache(context);
      } else {
        showToast(AppLocalizations.of(context)!.info_exception_shutdownfailed);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    // Code to run on initial launch
    if (_initRun && !StateManager().isDesktop) {
      _initRun = false;

      // Apply bar colours
      changeStatusColor(Theme.of(context).primaryColor);
      changeNavigationColor(Theme.of(context).bottomAppBarColor);
    }

    // Height of panel when fully expanded
    _panelHeightOpen = 500;

    return Scaffold(
      backgroundColor: Theme.of(context).canvasColor,

      extendBody: true,
      extendBodyBehindAppBar: true,

      appBar: PreferredSize(
          preferredSize: const Size.fromHeight(kToolbarHeight),
          child: Container(
            decoration: BoxDecoration(boxShadow: [
              BoxShadow(
                color: Theme.of(context).primaryColor,
                offset: const Offset(0, 3),
                spreadRadius: 25,
                blurRadius: 15,
              )
            ]),
            child: AppBar(
              elevation: 0,
              titleTextStyle: Theme.of(context).textTheme.headline6,
              backgroundColor: Theme.of(context).primaryColor,
              title: Padding(
                padding: const EdgeInsets.only(left: 5),
                child: Text(widget.title),
              ),
            ),
          )),

      // Body here...
      body: Stack(
        alignment: Alignment.topCenter,
        children: <Widget>[
          SlidingUpPanel(
            boxShadow: kElevationToShadow[3],
            color: Theme.of(context).bottomAppBarColor,
            borderRadius: const BorderRadius.only(
              topLeft: const Radius.circular(20),
              topRight: const Radius.circular(20),
            ),
            onPanelSlide: (double pos) => setState(() {
              _fabHeight = pos * (_panelHeightOpen - _panelHeightClosed) +
                  _initFabHeight;
              _fabPos = pos;
            }),
            maxHeight: _panelHeightOpen,
            minHeight: _panelHeightClosed,
            parallaxEnabled: true,
            parallaxOffset: .5,
            panelBuilder: (sc) => Panel().panelInterface(sc, context),
            header: Panel().panelHeader(context),

            // Main content
            body: Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: <Widget>[_stateView],
              ),
            ),
          ),

          // FAB layout
          Positioned(
            bottom: _fabHeight,
            left: 35,
            right: 35,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                AnimatedSwitcher(
                  duration: const Duration(milliseconds: 250),
                  transitionBuilder:
                      (Widget child, Animation<double> animation) =>
                          ScaleTransition(child: child, scale: animation),
                  child: !Server.serverRunning
                      ? SizedBox()
                      : FloatingActionButton(
                          elevation: 3,
                          backgroundColor: const Color.fromRGBO(194, 41, 33, 1),
                          foregroundColor:
                              const Color.fromRGBO(255, 255, 255, 1.0),
                          onPressed: () {
                            if (_actionButtonLoading) {
                              showToast(AppLocalizations.of(context)!
                                  .info_pending_fileprocessing_shutdown);
                            } else if (Server.serverRunning &&
                                !Server.serverPoweringDown) {
                              shutdownFAB();
                            } else {
                              showToast(AppLocalizations.of(context)!
                                  .info_pending_servershutdown);
                            }
                          },
                          child: Icon(
                            Icons.power_settings_new,
                            color: const Color.fromRGBO(255, 255, 255, 1.0),
                            size: 22.5,
                            semanticLabel: AppLocalizations.of(context)!
                                .fab_shutdownserver_label,
                          ),
                        ),
                ),
                FloatingActionButton(
                  elevation: 3,
                  backgroundColor: Theme.of(context).colorScheme.secondary,
                  foregroundColor: const Color.fromRGBO(255, 255, 255, 1.0),
                  onPressed: () {
                    importFile();
                  },
                  child: AnimatedSwitcher(
                    duration: const Duration(milliseconds: 300),
                    transitionBuilder:
                        (Widget child, Animation<double> animation) =>
                            ScaleTransition(child: child, scale: animation),
                    child: _actionButtonLoading
                        ? StateManager().loadingIndicator()
                        : Icon(
                            Icons.insert_drive_file,
                            color: const Color.fromRGBO(255, 255, 255, 1.0),
                            size: 20.0,
                            semanticLabel: AppLocalizations.of(context)!
                                .fab_selectfile_label,
                          ),
                  ),
                ),
              ],
            ),
          ),

          // Rebuild FAB with new position on resize
          LayoutBuilder(
            builder: (context, constraints) {
              SchedulerBinding.instance.addPostFrameCallback((_) {
                setState(() {
                  _fabHeight =
                      _fabPos * (_panelHeightOpen - _panelHeightClosed) +
                          _initFabHeight;
                });
              });
              return const SizedBox.shrink();
            },
          ),
        ],
      ),
    );
  }
}
