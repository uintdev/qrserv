import 'package:flutter/services.dart';
import 'package:flutter/material.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:oktoast/oktoast.dart';
import 'package:share_plus/share_plus.dart';
import 'statemanager.dart';

class ShareManager {
  // Share sheet
  static Future<void> _shareSheet(String url) async {
    await Share.share(url);
    return;
  }

  // Clipboard
  static Future<void> copyURL(String url, BuildContext context) async {
    await Clipboard.setData(ClipboardData(text: url));
    showToast(AppLocalizations.of(context)!.page_imported_share_clipboard);
    return;
  }

  // Determine what share method to use
  Future<void> share(String url, BuildContext context) async {
    if (!StateManager().isDesktop) {
      await _shareSheet(url);
    } else {
      await copyURL(url, context);
    }
    return;
  }
}
