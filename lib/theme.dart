import 'package:flutter/material.dart';

class FlutterDark {
  static ThemeData dark(ThemeData template) {
    var themeData = template;
    var newThemeData = themeData.copyWith(
      canvasColor: Color.fromRGBO(28, 27, 30, 1),
      primaryColor: Color.fromRGBO(28, 27, 30, 1),
      indicatorColor: Color.fromRGBO(91, 93, 213, 1),
      textTheme: TextTheme(
        titleLarge: TextStyle(
          fontFamily: 'Poppins',
          fontSize: 16,
        ),
        bodyMedium: TextStyle(
          fontFamily: 'Poppins',
          fontSize: 13.5,
        ),
      ),
      colorScheme: themeData.colorScheme.copyWith(
          secondaryContainer: Color.fromRGBO(91, 93, 213, 1),
          secondary: Color.fromARGB(255, 191, 180, 229),
          background: Color.fromRGBO(28, 32, 42, 1)),
    );
    return newThemeData;
  }
}
