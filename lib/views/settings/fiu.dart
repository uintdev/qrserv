import 'package:flutter/material.dart';
import 'package:qrserv/components/preferences.dart';

class FIU {
  static bool state = false;

  Future<void> toggle(BuildContext context, StateSetter setState) async {
    state = !state;
    await Preferences().write(Preferences.PREF_CLIENT_FIU, state);
    setState(() {});
  }
}
