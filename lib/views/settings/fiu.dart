import 'package:flutter/material.dart';
import 'package:qrserv/components/preferences.dart';

class FIU {
  // Private constructor to prevent instantiation
  FIU._();

  static bool state = false;

  static Future<void> toggle(BuildContext context, StateSetter setState) async {
    state = !state;
    await Preferences.write(Preferences.PREF_CLIENT_FIU, state);
    setState(() {});
  }
}
