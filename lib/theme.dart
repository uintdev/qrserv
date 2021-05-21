import 'package:flutter/material.dart';

class FlutterDark {
  static ThemeData dark(ThemeData template) {
    var themeData = template;
    var newThemeData = themeData.copyWith(
      backgroundColor: Color.fromRGBO(28, 32, 42, 1),
      canvasColor: Color.fromRGBO(20, 20, 20, 1),
      primaryColor: Color.fromRGBO(20, 20, 20, 1),
      bottomAppBarColor: Color.fromRGBO(44, 44, 44, 1),
      cardColor: Color.fromRGBO(54, 54, 54, 1),
      indicatorColor: Color.fromRGBO(91, 93, 213, 1),
      colorScheme: themeData.colorScheme.copyWith(
        secondary: Color.fromRGBO(91, 93, 213, 1),
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
