import 'package:flutter/material.dart';

class FlutterDark {
  static ThemeData dark(ThemeData template) {
    var themeData = template;
    var newThemeData = themeData.copyWith(
      useMaterial3: true,
      canvasColor: const Color.fromRGBO(28, 27, 30, 1),
      primaryColor: const Color.fromRGBO(28, 27, 30, 1),
      indicatorColor: const Color.fromRGBO(91, 93, 213, 1),
      textTheme: TextTheme(
        titleLarge: const TextStyle(
          fontFamily: 'Poppins',
          fontSize: 16,
        ),
        bodyMedium: const TextStyle(
          fontFamily: 'Poppins',
          fontSize: 13.5,
        ),
      ),
      colorScheme: themeData.colorScheme.copyWith(
        secondaryContainer: const Color.fromRGBO(91, 93, 213, 1),
        secondary: const Color.fromARGB(255, 191, 180, 229),
        background: const Color.fromRGBO(28, 32, 42, 1),
      ),
    );
    return newThemeData;
  }
}
