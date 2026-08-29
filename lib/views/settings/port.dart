import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart';
import '../../l10n/generated/app_localizations.dart';
import 'package:oktoast/oktoast.dart';
import '../../components/server.dart';
import '../../components/network.dart';
import '../../components/preferences.dart';

class Port {
  final TextEditingController _fieldController = TextEditingController();
  final int portMin = 1024;
  final int portMax = 65535;

  Future<void> portDialog(BuildContext context) async {
    String currentPortNumber = '';

    final int? fetchPortNumber = await Preferences.read(
      Preferences.PREF_SERVER_PORT,
    );

    if (fetchPortNumber != null) {
      currentPortNumber = fetchPortNumber.toString();
    }

    _fieldController.text = currentPortNumber;

    await showDialog(
      context: context,
      useRootNavigator: false,
      builder: (contextDialog) => StatefulBuilder(
        builder: (BuildContext context, StateSetter setState) {
          return Dialog(
            child: Padding(
              padding: EdgeInsets.fromLTRB(30, 20, 30, 10),
              child: portDialogContents(context, contextDialog, setState),
            ),
          );
        },
      ),
    );
  }

  Column portDialogContents(
    BuildContext context,
    BuildContext contextDialog,
    StateSetter setState,
  ) {
    return Column(
      mainAxisSize: .min,
      children: [
        Align(
          alignment: .center,
          child: Text(
            AppLocalizations.of(context)!.settings_server_port_list_title,
            style: const TextStyle(
              fontSize: 25,
              fontVariations: [FontVariation('wght', 500)],
            ),
          ),
        ),
        const SizedBox(height: 5),
        Text(
          AppLocalizations.of(
            context,
          )!.settings_server_port_dialog_description.format([portMin, portMax]),
          style: const TextStyle(
            fontSize: 13,
            fontVariations: [FontVariation('wght', 500)],
          ),
        ),
        if (Server.serverRunning)
          Column(
            children: [
              SizedBox(height: 5),
              Text(
                AppLocalizations.of(
                  context,
                )!.settings_server_port_dialog_serveractive,
                style: const TextStyle(
                  fontSize: 13,
                  fontVariations: [FontVariation('wght', 500)],
                ),
              ),
            ],
          ),
        SizedBox(height: 15),
        TextField(
          controller: _fieldController,
          keyboardType: TextInputType.number,
          inputFormatters: [
            FilteringTextInputFormatter.digitsOnly,
            NoLeadingZerosTextInputFormatter(),
          ],
          autofocus: true,
          onChanged: (value) => setState(() {}),
          cursorColor: Theme.of(context).primaryColor,
          style: TextStyle(color: Theme.of(context).primaryColor),
          decoration: InputDecoration(
            labelText: AppLocalizations.of(
              context,
            )!.settings_server_port_list_title,
            border: OutlineInputBorder(),
            focusedBorder: OutlineInputBorder(
              borderSide: BorderSide(color: Theme.of(context).primaryColor),
            ),
            labelStyle: TextStyle(color: Theme.of(context).primaryColor),
          ),
        ),
        SizedBox(height: 15),
        ElevatedButton(
          child: Text(
            AppLocalizations.of(context)!.settings_server_port_dialog_submit,
          ),
          style: ElevatedButton.styleFrom(
            foregroundColor: Theme.of(context).primaryColor,
            animationDuration: Duration.zero,
          ),
          onPressed: portSubmissionValidation()
              ? portSubmission(context, contextDialog)
              : null,
        ),
      ],
    );
  }

  // Parses the field text into a port number.
  // Returns null for an empty field (valid — clears the saved port)
  // or for text that isn't a valid number in range.
  int? _parsePort() {
    final String text = _fieldController.text;
    if (text.isEmpty) return null;

    final int? value = int.tryParse(text);
    if (value == null) return null;
    if (value < portMin || value > portMax) return null;

    return value;
  }

  bool portSubmissionValidation() {
    final String text = _fieldController.text;
    if (text.isEmpty) return true;

    return _parsePort() != null;
  }

  VoidCallback? portSubmission(
    BuildContext context,
    BuildContext contextDialog,
  ) {
    return () async {
      final String text = _fieldController.text;

      if (text.isEmpty) {
        await Preferences.write(Preferences.PREF_SERVER_PORT, null);
        showToast(
          AppLocalizations.of(context)!.settings_server_port_dialog_saved,
        );
        Navigator.of(contextDialog).pop();
        return;
      }

      final int? value = _parsePort();
      if (value == null) return;

      final bool portUsed = await Network.checkPortUsed(value);
      if (portUsed) {
        showToast(
          AppLocalizations.of(context)!.settings_server_port_dialog_portinuse,
        );
        return;
      }

      await Preferences.write(Preferences.PREF_SERVER_PORT, value);
      showToast(
        AppLocalizations.of(context)!.settings_server_port_dialog_saved,
      );
      Navigator.of(contextDialog).pop();
    };
  }
}

extension StringFormat on String {
  String format(List<dynamic> values) {
    String result = this;
    for (final value in values) {
      result = result.replaceFirst('%s', value.toString());
    }
    return result;
  }
}

class NoLeadingZerosTextInputFormatter extends TextInputFormatter {
  @override
  TextEditingValue formatEditUpdate(
    TextEditingValue oldValue,
    TextEditingValue newValue,
  ) {
    final newText = newValue.text;

    final userTypedWhenThereWasZero =
        newText.startsWith("0") && newText.length > 1;

    if (!userTypedWhenThereWasZero) return newValue;

    final regExp = RegExp(r'^0+(?=.)');
    final textWithoutLeadingZeros = newText.replaceAll(regExp, "");

    return TextEditingValue(
      text: textWithoutLeadingZeros,
      selection: newValue.selection.copyWith(
        baseOffset: newText.length - 1,
        extentOffset: newText.length - 1,
      ),
    );
  }
}
