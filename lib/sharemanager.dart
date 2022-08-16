import 'dart:io';
import 'package:flutter/services.dart';
import 'package:flutter/material.dart';
import 'package:path/path.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:oktoast/oktoast.dart';
import 'package:share_plus/share_plus.dart';
import 'statemanager.dart';
import 'cachemanager.dart';
import 'filemanager.dart';
import 'server.dart';

class ShareManager {
  // Share sheet
  static Future<void> _shareSheet(String url) async {
    await Share.share(url);
    return;
  }

  // Clipboard
  static Future<void> _copyURL(String url, BuildContext context) async {
    await Clipboard.setData(ClipboardData(text: url));
    showToast(AppLocalizations.of(context)!.page_imported_share_clipboard);
    return;
  }

  // Determine what share method to use
  Future<void> share(String url, BuildContext context) async {
    if (!StateManager().isDesktop) {
      await _shareSheet(url);
    } else {
      await _copyURL(url, context);
    }
    return;
  }

  // Manage sent content
  Future importShared(BuildContext context, String file) async {
    // Cache handling
    if (FileManager.currentFullPath != '' &&
        FileManager.currentFullPath != file &&
        Server().fileExists(FileManager.currentFullPath)) {
      CacheManager().deleteCache(context, [FileManager.currentFullPath]);
    }

    // Set file information
    FileManager.currentFile = basename(file);
    FileManager.currentFullPath = file;
    FileManager.currentPath = dirname(file);
    FileManager.currentLength = File(file).lengthSync();

    // Set import status
    FileManager.fileImported = true;
  }
}
