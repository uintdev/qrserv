import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'package:flutter/services.dart';
import 'theme.dart';
import 'filepicker.dart';
import 'cachemanager.dart';
import 'statemanager.dart';
import 'server.dart';
import 'panel.dart';
import 'package:window_size/window_size.dart';
import 'package:sliding_up_panel/sliding_up_panel.dart';
import 'package:flutter_statusbarcolor/flutter_statusbarcolor.dart';
import 'package:oktoast/oktoast.dart';
import 'package:flutter_translate/flutter_translate.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Window information for desktop platforms
  if (StateManager().isDesktop) {
    setWindowTitle('QRServ');
    setWindowMinSize(const Size(650, 1210));
    setWindowMaxSize(Size.infinite);
  }

  // Clear cache on launch
  CacheManager().deleteCache();

  // TODO: Potentially rework i18n
  var delegate = await LocalizationDelegate.create(
      fallbackLocale: 'en',
      supportedLocales: ['en', 'fr', 'de', 'es', 'hu', 'pt']);

  runApp(MaterialApp(home: LocalizedApp(delegate, QRServ())));
}

class QRServ extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    // Portrait only
    SystemChrome.setPreferredOrientations([
      DeviceOrientation.portraitUp,
      DeviceOrientation.portraitDown,
    ]);

    var localizationDelegate = LocalizedApp.of(context).delegate;

    return OKToast(
        // Toast properties
        position: ToastPosition.bottom,
        textPadding: EdgeInsets.fromLTRB(25, 16, 25, 16),
        backgroundColor: Color.fromRGBO(60, 60, 60, 1.0),
        duration: Duration(milliseconds: 3500),
        radius: 30,
        child: LocalizationProvider(
          state: LocalizationProvider.of(context).state,
          child: MaterialApp(
            localizationsDelegates: [
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              localizationDelegate
            ],
            supportedLocales: localizationDelegate.supportedLocales,
            locale: localizationDelegate.currentLocale,
            theme: FlutterDark.dark(ThemeData.dark()),
            home: PageState(),
          ),
        ));
  }
}

class PageState extends StatefulWidget {
  PageState({Key key}) : super(key: key);

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

  @override
  void initState() {
    super.initState();

    _fabHeight = _initFabHeight;
    WidgetsBinding.instance?.addObserver(this);
  }

  @override
  dispose() {
    WidgetsBinding.instance?.removeObserver(this);
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
      showToast(translate('info.exception.statusbar.msg') + e.toString());
    }
  }

  changeNavigationColor(Color color) async {
    try {
      await FlutterStatusbarcolor.setNavigationBarColor(color, animate: true);
    } on PlatformException catch (e) {
      showToast(translate('info.exception.navigationbar.msg') + e.toString());
    }
  }

  Widget _stateView = StateManagerPage();

  bool _actionButtonLoading = false;

  // File selection handling
  void importFile() async {
    // Prevent further execution if still loading
    if (_actionButtonLoading) {
      showToast(translate('info.pending.fileprocessing.msg'));
      return;
    }

    // Update button state
    setState(() {
      _actionButtonLoading = true;
    });

    // Attempt file import
    await FilePicker().selectFile().whenComplete(() {
      if (FilePicker.fileImported) {
        // Update state
        setState(() {
          _stateView = StateManagerPage();
          _actionButtonLoading = false;
        });
      }
    }).onError((error, _) {
      String _exceptionData = error.reason();

      if (_exceptionData == 'read_external_storage_denied') {
        // System denied storage access
        setState(() {
          _stateView = StateManager().msgPage(3);
        });
      } else if (_exceptionData == 'selection_canceled') {
        // User cancelled selection...
      } else {
        // Unknown exception -- inform user
        showToast(
            translate('info.exception.fileselection.msg') + _exceptionData);
      }
      // Revert FAB state
      _actionButtonLoading = false;
    });
  }

  // Handle server shutdown via FAB
  void shutdownFAB() async {
    await Server().shutdownServer().whenComplete(() {
      if (!Server.serverRunning) {
        setState(() {
          FilePicker.fileImported = false;
          _stateView = StateManagerPage();
        });
        CacheManager().deleteCache();
      } else {
        showToast(translate('info.exception.shutdownfailed.msg'));
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    // Apply bar colours
    if (_initRun && !StateManager().isDesktop) {
      _initRun = false;
      changeStatusColor(Theme.of(context).primaryColor);
      changeNavigationColor(Theme.of(context).bottomAppBarColor);
    }

    // Height of panel when fully expanded
    _panelHeightOpen = 460;

    return Scaffold(
      backgroundColor: Theme.of(context).canvasColor,

      extendBody: true,
      extendBodyBehindAppBar: true,

      appBar: AppBar(
        title: Text(
          'QRServ',
          style: TextStyle(
            fontSize: 18,
          ),
        ),
        backgroundColor: Theme.of(context).primaryColor,
        elevation: 0,
      ),

      // Body here...
      body: Stack(
        alignment: Alignment.topCenter,
        children: <Widget>[
          SlidingUpPanel(
            boxShadow: kElevationToShadow[3],
            color: Theme.of(context).bottomAppBarColor,
            borderRadius: BorderRadius.only(
              topLeft: Radius.circular(20),
              topRight: Radius.circular(20),
            ),
            onPanelSlide: (double pos) => setState(() {
              _fabHeight = pos * (_panelHeightOpen - _panelHeightClosed) +
                  _initFabHeight;
              _fabPos = pos;
            }),
            maxHeight: _panelHeightOpen,
            minHeight: _panelHeightClosed,
            parallaxEnabled: true,
            parallaxOffset: .15,
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
                          backgroundColor: Theme.of(context).buttonColor,
                          foregroundColor: Colors.white,
                          onPressed: () {
                            if (Server.serverRunning &&
                                !Server.serverPoweringDown) {
                              shutdownFAB();
                            } else {
                              showToast(
                                  translate('info.pending.servershutdown.msg'));
                            }
                          },
                          child: Icon(
                            Icons.power_settings_new,
                            color: Colors.white,
                            size: 22.5,
                            semanticLabel:
                                translate('fab.shutdownserver.label'),
                          ),
                        ),
                ),
                FloatingActionButton(
                  elevation: 3,
                  backgroundColor: Theme.of(context).accentColor,
                  foregroundColor: Colors.white,
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
                            color: Colors.white,
                            size: 20.0,
                            semanticLabel: translate('fab.selectfile.label'),
                          ),
                  ),
                ),
              ],
            ),
          ),

          // Rebuild FAB with new position on resize
          LayoutBuilder(
            builder: (context, constraints) {
              SchedulerBinding.instance?.addPostFrameCallback((_) {
                setState(() {
                  _fabHeight =
                      _fabPos * (_panelHeightOpen - _panelHeightClosed) +
                          _initFabHeight;
                });
              });
              return SizedBox.shrink();
            },
          ),
        ],
      ),
    );
  }
}
