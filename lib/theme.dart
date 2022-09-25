import 'package:flutter/material.dart';

class FlutterDark {
  static ThemeData dark(ThemeData template) {
    var themeData = template;
    var newThemeData = themeData.copyWith(
      backgroundColor: Color.fromRGBO(28, 32, 42, 1),
      canvasColor: Color.fromRGBO(28, 27, 30, 1),
      primaryColor: Color.fromRGBO(28, 27, 30, 1),
      bottomAppBarColor: Color.fromRGBO(28, 27, 30, 1),
      indicatorColor: Color.fromRGBO(91, 93, 213, 1),
      colorScheme: themeData.colorScheme.copyWith(
        secondaryContainer: Color.fromRGBO(91, 93, 213, 1),
        secondary: Color.fromARGB(255, 191, 180, 229),
      ),
      textTheme: TextTheme(
        headline6: TextStyle(
          fontFamily: 'Poppins',
          fontSize: 16,
        ),
        bodyText2: TextStyle(
          fontFamily: 'Poppins',
          fontSize: 13.5,
        ),
      ),
    );
    return newThemeData;
  }
}
