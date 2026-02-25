import 'package:flutter/services.dart';
import 'package:flutter/material.dart';
import '../l10n/generated/app_localizations.dart';
import 'package:oktoast/oktoast.dart';
import 'package:share_plus/share_plus.dart';

class ShareManager {
  // Private constructor to prevent instantiation
  ShareManager._();

  // Share sheet
  static Future<void> shareSheet(String url) async {
    await SharePlus.instance.share(ShareParams(uri: Uri.parse(url)));
  }

  // Clipboard
  static Future<void> copyURL(String url, BuildContext context) async {
    await Clipboard.setData(ClipboardData(text: url));
    showToast(AppLocalizations.of(context)!.page_imported_share_clipboard);
  }
}
