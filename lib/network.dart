import 'package:flutter/material.dart';
import 'dart:io';
import 'server.dart';
import 'filepicker.dart';
import 'package:oktoast/oktoast.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';

class Network {
  // Default port number
  static int port = 0;

  // Initialise interface lists
  List<String> _interfaceList = [];
  List<String> ipv4List = [];
  List<String> ipv6List = [];

  // Interface list builder
  Future _internalIP() async {
    // Reset lists
    _interfaceList = [];
    ipv4List = [];
    ipv6List = [];

    // Collect currently used interfaces
    for (NetworkInterface interface in await NetworkInterface.list()) {
      for (InternetAddress addr in interface.addresses) {
        // Filter out 192.168.*.0-1
        bool filterList = !(addr.rawAddress[0] == 192 &&
            addr.rawAddress[1] == 168 &&
            addr.rawAddress[3] < 2);
        if (filterList) {
          // print(interface.name);
          // print(addr.address);

          // Organise IPs into their own version lists
          if (addr.type.name == 'IPv4') {
            ipv4List.add(addr.address);
          } else if (addr.type.name == 'IPv6') {
            ipv6List.add(addr.address);
          }
        }
      }
    }
    // Create and organise interface list
    ipv4List = ipv4List..sort();
    ipv6List = ipv6List..sort();
    _interfaceList = new List.from(ipv4List.reversed)..addAll(ipv6List);
  }

  // Get full list of IPs and unused port
  Future<Map<String, dynamic>> fetchInterfaces(BuildContext context) async {
    // Prepare interface list and fetch unused port for server
    await _internalIP();

    // Server init
    if (!Server.serverRunning &&
        Server().fileExists(FilePicker().readInfo()['path'])) {
      await Server().http().onError((error, _) {
        // Selected port should already be uniquely unused
        // by other services at the time, but just as a precaution...
        showToast(AppLocalizations.of(context)!.info_exception_portinuse +
            error.toString());
        Server.serverException = true;
      });
    }

    // Shutdown server if marked
    if (!Server().fileExists(FilePicker().readInfo()['path'])) {
      await Server().shutdownServer(context);
    }

    Map<String, dynamic> networkData = {
      'interfaces': _interfaceList,
      'port': port
    };

    return networkData;
  }

  // Determine IP version
  bool checkIPV4(String? ip) {

    if (ip == null) return true;

    bool _versionType;

    RegExp regExp = new RegExp(
      r"^[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}$",
      caseSensitive: false,
      multiLine: false,
    );

    _versionType = regExp.hasMatch(ip);

    return _versionType;
  }
}
