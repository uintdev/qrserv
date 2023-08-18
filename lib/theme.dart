import 'package:flutter/material.dart';

class QRSTheme {
  static ThemeData light(ThemeData template) {
    var themeData = template;
    var newThemeData = themeData.copyWith(
      canvasColor: const Color.fromRGBO(237, 232, 243, 1),
      primaryColor: const Color.fromRGBO(0, 0, 0, 1),
      indicatorColor: const Color.fromRGBO(91, 93, 213, 1),
      splashColor: const Color.fromRGBO(99, 81, 159, 0.3),
      textTheme: TextTheme(
        titleLarge: const TextStyle(
          color: const Color.fromRGBO(0, 0, 0, 1),
          fontFamily: 'Poppins',
          fontSize: 16,
        ),
        bodyMedium: const TextStyle(
          color: const Color.fromRGBO(0, 0, 0, 1),
          fontFamily: 'Poppins',
          fontSize: 13.5,
        ),
      ),
      cardTheme: CardTheme(
        color: Color.fromRGBO(227, 222, 233, 1),
      ),
      tooltipTheme: TooltipThemeData(
        textStyle: TextStyle(
          color: Color.fromRGBO(255, 255, 255, 1),
        ),
      ),
      colorScheme: themeData.colorScheme.copyWith(
        secondaryContainer: const Color.fromRGBO(91, 93, 213, 1),
        secondary: const Color.fromRGBO(191, 180, 229, 1),
        background: Color.fromRGBO(209, 219, 224, 1),
      ),
    );
    return newThemeData;
  }

  static ThemeData dark(ThemeData template) {
    var themeData = template;
    var newThemeData = themeData.copyWith(
      canvasColor: const Color.fromRGBO(37, 35, 41, 1),
      primaryColor: const Color.fromRGBO(255, 255, 255, 1),
      indicatorColor: const Color.fromRGBO(91, 93, 213, 1),
      splashColor: const Color.fromRGBO(99, 81, 159, 0.3),
      textTheme: TextTheme(
        titleLarge: const TextStyle(
          color: const Color.fromRGBO(255, 255, 255, 1),
          fontFamily: 'Poppins',
          fontSize: 16,
        ),
        bodyMedium: const TextStyle(
          color: const Color.fromRGBO(255, 255, 255, 1),
          fontFamily: 'Poppins',
          fontSize: 13.5,
        ),
      ),
      cardTheme: CardTheme(
        color: const Color.fromRGBO(46, 44, 54, 1),
      ),
      tooltipTheme: TooltipThemeData(
        textStyle: TextStyle(
          color: Color.fromRGBO(0, 0, 0, 1),
        ),
      ),
      colorScheme: themeData.colorScheme.copyWith(
        secondaryContainer: const Color.fromRGBO(91, 93, 213, 1),
        secondary: const Color.fromRGBO(191, 180, 229, 1),
        background: const Color.fromRGBO(28, 32, 42, 1),
      ),
    );
    return newThemeData;
  }
}
