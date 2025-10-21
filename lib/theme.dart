import 'package:flutter/material.dart';

class QRSTheme {
  static String fontFamily = 'Nunito';
  static double titleLargeSize = 16;
  static double titleSmallSize = 13.5;

  static ThemeData light(ThemeData template) {
    var themeData = template;
    var newThemeData = themeData.copyWith(
      canvasColor: const Color.fromRGBO(237, 232, 243, 1),
      primaryColor: const Color.fromRGBO(30, 30, 45, 1),
      splashColor: const Color.fromRGBO(99, 81, 159, 0.3),
      dialogTheme: DialogThemeData(
        backgroundColor: const Color.fromRGBO(237, 232, 243, 1),
      ),
      iconTheme: IconThemeData(color: const Color.fromRGBO(30, 30, 45, 1)),
      textTheme: TextTheme(
        titleLarge: TextStyle(
          color: const Color.fromRGBO(30, 30, 45, 1),
          fontFamily: fontFamily,
          fontSize: titleLargeSize,
        ),
        bodyMedium: TextStyle(
          color: const Color.fromRGBO(30, 30, 45, 1),
          fontFamily: fontFamily,
          fontSize: titleSmallSize,
        ),
      ),
      cardTheme: CardThemeData(color: const Color.fromRGBO(227, 222, 233, 1)),
      tooltipTheme: TooltipThemeData(
        textStyle: TextStyle(color: const Color.fromRGBO(255, 255, 255, 1)),
      ),
      colorScheme: themeData.colorScheme.copyWith(
        secondaryContainer: const Color.fromRGBO(91, 93, 213, 1),
        secondary: const Color.fromRGBO(191, 180, 229, 1),
      ),
      tabBarTheme: TabBarThemeData(
        indicatorColor: const Color.fromRGBO(91, 93, 213, 1),
      ),
    );
    return newThemeData;
  }

  static ThemeData dark(ThemeData template) {
    var themeData = template;
    var newThemeData = themeData.copyWith(
      canvasColor: const Color.fromRGBO(37, 35, 41, 1),
      primaryColor: const Color.fromRGBO(255, 255, 255, 1),
      splashColor: const Color.fromRGBO(99, 81, 159, 0.3),
      dialogTheme: DialogThemeData(
        backgroundColor: const Color.fromRGBO(37, 35, 41, 1),
      ),
      iconTheme: IconThemeData(color: const Color.fromRGBO(255, 255, 255, 1)),
      textTheme: TextTheme(
        titleLarge: TextStyle(
          color: const Color.fromRGBO(255, 255, 255, 1),
          fontFamily: fontFamily,
          fontSize: titleLargeSize,
        ),
        bodyMedium: TextStyle(
          color: const Color.fromRGBO(255, 255, 255, 1),
          fontFamily: fontFamily,
          fontSize: titleSmallSize,
        ),
      ),
      cardTheme: CardThemeData(color: const Color.fromRGBO(46, 44, 54, 1)),
      tooltipTheme: TooltipThemeData(
        textStyle: TextStyle(color: const Color.fromRGBO(0, 0, 0, 1)),
      ),
      colorScheme: themeData.colorScheme.copyWith(
        secondaryContainer: const Color.fromRGBO(91, 93, 213, 1),
        secondary: const Color.fromRGBO(191, 180, 229, 1),
      ),
      tabBarTheme: TabBarThemeData(
        indicatorColor: const Color.fromRGBO(91, 93, 213, 1),
      ),
    );
    return newThemeData;
  }
}
