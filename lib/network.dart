import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:oktoast/oktoast.dart';
import 'server.dart';
import 'filemanager.dart';
import 'statemanager.dart';

class Network {
  // Default port number
  static int port = 0;

  // Initialise interface lists
  static List<String> interfaceList = [];
  List<String> _ipv4List = [];
  List<String> _ipv6List = [];

  // Interface list builder
  Future internalIP() async {
    // Reset lists
    Network.interfaceList = [];
    _ipv4List = [];
    _ipv6List = [];

    // Collect currently used interfaces
    for (NetworkInterface interface in await NetworkInterface.list()) {
      for (InternetAddress addr in interface.addresses) {
        // Filter out 192.168.*.0-1
        bool filterList = !(addr.rawAddress[0] == 192 &&
            addr.rawAddress[1] == 168 &&
            addr.rawAddress[3] < 2);
        if (filterList) {
          // Organise IPs into their own version lists
          if (addr.type.name == 'IPv4') {
            _ipv4List.add(addr.address);
          } else if (addr.type.name == 'IPv6') {
            _ipv6List.add(addr.address.split('%')[0]);
          }
        }
      }
    }
    // Create and organise interface list
    _ipv4List = _ipv4List..sort();
    _ipv6List = _ipv6List..sort();
    Network.interfaceList = List.from(_ipv4List.reversed)..addAll(_ipv6List);
  }

  // Get full list of IPs and unused port
  Future<Map<String, dynamic>> fetchInterfaces(BuildContext context) async {
    // Prepare interface list and fetch unused port for server
    await internalIP();

    // Server init
    if (!Server.serverRunning &&
        Server().fileExists(FileManager().readInfo()['path'])) {
      await Server().http(context).onError((error, _) {
        // Selected port should already be uniquely unused
        // by other services at the time, but just as a precaution...
        showToast(AppLocalizations.of(context)!.info_exception_portinuse +
            error.toString());
        Server.serverException = true;
      });
    }

    // Shutdown server if marked
    // if (!StateManager().fileUntampered) {
    if ((StateManager.fileTampered == PageType.filemodified) ||
        !Server().fileExists(FileManager().readInfo()['path'])) {
      // TODO: add check for state type
      await Server().shutdownServer(context);
    }

    Map<String, dynamic> networkData = {
      'interfaces': interfaceList,
      'port': port
    };

    return networkData;
  }

  // Determine IP version
  bool checkIPV4(String? ip) {
    if (ip == null) return true;

    bool _versionType;

    RegExp regExp = RegExp(
      r"^[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}$",
    );

    _versionType = regExp.hasMatch(ip);

    return _versionType;
  }
}
