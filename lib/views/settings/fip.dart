import 'package:flutter/material.dart';
import 'package:qrserv/components/preferences.dart';

class FIP {
  static bool state = false;

  Future<void> toggle(BuildContext context, StateSetter setState) async {
    state = !state;
    await Preferences().write(Preferences.PREF_CLIENT_FIP, state);
    setState(() {});
  }
}
