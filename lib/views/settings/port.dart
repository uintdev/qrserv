import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../l10n/generated/app_localizations.dart';
import 'package:oktoast/oktoast.dart';

class Port {
  final TextEditingController _fieldController = TextEditingController();
  final int portMin = 1024;
  final int portMax = 65535;

  Future portDialog(BuildContext context) async {
    await showDialog(
      context: context,
      useRootNavigator: false,
      builder: (contextDialog) => Dialog(
        child: Padding(
          padding: .fromLTRB(30, 20, 30, 10),
          child: Container(child: portDialogContents(context, contextDialog)),
        ),
      ),
    );
  }

  Column portDialogContents(BuildContext context, BuildContext contextDialog) {
    return Column(
      mainAxisSize: .min,
      children: [
        Align(
          alignment: .center,
          child: Container(
            child: Text(
              AppLocalizations.of(context)!.settings_server_port_list_title,
              style: const TextStyle(
                fontSize: 25,
                fontVariations: [FontVariation('wght', 500)],
              ),
            ),
          ),
        ),
        const SizedBox(height: 5),
        // TODO: use localization here
        Text(
          'Port number range must be %s - %s. Leave empty for a random port.'
              .format([portMin, portMax]),
          style: const TextStyle(
            fontSize: 13,
            fontVariations: [FontVariation('wght', 500)],
          ),
        ),
        SizedBox(height: 5),
        Text(
          'Changes apply on server restart.',
          style: const TextStyle(
            fontSize: 13,
            fontVariations: [FontVariation('wght', 500)],
          ),
        ),
        SizedBox(height: 15),
        TextField(
          controller: _fieldController,
          keyboardType: TextInputType.number,
          inputFormatters: [FilteringTextInputFormatter.digitsOnly],
          autofocus: true,
          cursorColor: Theme.of(context).primaryColor,
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
          // TODO: use localization here
          child: Text('Set Port'),
          style: ElevatedButton.styleFrom(
            foregroundColor: Theme.of(context).primaryColor,
          ),
          onPressed: () {
            final String text = _fieldController.text;

            if (text.isEmpty) {
              // TODO: use localization here
              // TODO: add storage logic
              showToast('Saved changes');
              Navigator.pop(contextDialog);
              return;
            }

            final int? value = text.isEmpty ? null : int.tryParse(text);

            if (value == null) {
              // TODO: use localization here
              showToast('No valid integer entered');
              return;
            }

            if (!(value >= portMin && value <= portMax)) {
              // TODO: use localization here
              showToast('Port number out of range');
              return;
            }

            // TODO: add at-the-time port use check

            // TODO: add storage logic

            showToast('Saved changes');
            Navigator.pop(contextDialog);
          },
        ),
      ],
    );
  }
}

extension StringFormat on String {
  String format(List<dynamic> values) {
    String result = this;
    for (var value in values) {
      result = result.replaceFirst('%s', value.toString());
    }
    return result;
  }
}
