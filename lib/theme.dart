import 'package:flutter/material.dart';

class QRSTheme {
  static String fontFamily = 'Nunito';
  static double titleLargeSize = 16;
  static double titleSmallSize = 13.5;

  static ThemeData light(ThemeData template) {
    var themeData = template;
    var newThemeData = themeData.copyWith(
      canvasColor: const .fromRGBO(237, 232, 243, 1),
      primaryColor: const .fromRGBO(30, 30, 45, 1),
      splashColor: const .fromRGBO(99, 81, 159, 0.3),
      dialogTheme: DialogThemeData(
        backgroundColor: const .fromRGBO(237, 232, 243, 1),
      ),
      iconTheme: IconThemeData(color: const .fromRGBO(30, 30, 45, 1)),
      textTheme: TextTheme(
        titleLarge: TextStyle(
          color: const .fromRGBO(30, 30, 45, 1),
          fontFamily: fontFamily,
          fontSize: titleLargeSize,
        ),
        bodyMedium: TextStyle(
          color: const .fromRGBO(30, 30, 45, 1),
          fontFamily: fontFamily,
          fontSize: titleSmallSize,
        ),
      ),
      cardTheme: CardThemeData(color: const .fromRGBO(227, 222, 233, 1)),
      tooltipTheme: TooltipThemeData(
        textStyle: TextStyle(color: const .fromRGBO(255, 255, 255, 1)),
      ),
      colorScheme: themeData.colorScheme.copyWith(
        secondaryContainer: const .fromRGBO(91, 93, 213, 1),
        secondary: const .fromRGBO(191, 180, 229, 1),
      ),
      tabBarTheme: TabBarThemeData(
        indicatorColor: const .fromRGBO(91, 93, 213, 1),
      ),
    );
    return newThemeData;
  }

  static ThemeData dark(ThemeData template) {
    var themeData = template;
    var newThemeData = themeData.copyWith(
      canvasColor: const .fromRGBO(37, 35, 41, 1),
      primaryColor: const .fromRGBO(255, 255, 255, 1),
      splashColor: const .fromRGBO(99, 81, 159, 0.3),
      dialogTheme: DialogThemeData(
        backgroundColor: const .fromRGBO(37, 35, 41, 1),
      ),
      iconTheme: IconThemeData(color: const .fromRGBO(255, 255, 255, 1)),
      textTheme: TextTheme(
        titleLarge: TextStyle(
          color: const .fromRGBO(255, 255, 255, 1),
          fontFamily: fontFamily,
          fontSize: titleLargeSize,
        ),
        bodyMedium: TextStyle(
          color: const .fromRGBO(255, 255, 255, 1),
          fontFamily: fontFamily,
          fontSize: titleSmallSize,
        ),
      ),
      cardTheme: CardThemeData(color: const .fromRGBO(46, 44, 54, 1)),
      tooltipTheme: TooltipThemeData(
        textStyle: TextStyle(color: const .fromRGBO(0, 0, 0, 1)),
      ),
      colorScheme: themeData.colorScheme.copyWith(
        secondaryContainer: const .fromRGBO(91, 93, 213, 1),
        secondary: const .fromRGBO(191, 180, 229, 1),
      ),
      tabBarTheme: TabBarThemeData(
        indicatorColor: const .fromRGBO(91, 93, 213, 1),
      ),
    );
    return newThemeData;
  }
}
