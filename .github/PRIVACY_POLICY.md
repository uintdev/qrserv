# QRServ Privacy Policy

QRServ does not collect or send any information that might be personal or personally identifiable. There is no in-app data collection.

QRServ hosts content that the user specifically selects and that selection is then made available on the network of the IP address selected in the app, or on every network the device being utilized is connected to if listening on all interfaces is enabled in settings. This could be on a Local Area Network (including mobile tethering and common access points), Wide Area Network (depending on the network configuration) and/or a connected VPN.
When the private hotspot option is used (Android 13 or later), QRServ instead creates a Wi-Fi network of its own and the selection is made available only on that network, to devices that are given its network name and password.
QRServ checks on the device whether Android's "Block connections without VPN" setting is on, only to warn that other devices may be blocked from connecting. This is not stored or sent anywhere.
The GitHub version can check for a newer release when "Check for updates" is tapped in settings. Only then does QRServ contact GitHub (api.github.com), which sees the device's IP address as any website would. Nothing else is sent, and the app never checks on its own.
QRServ's on-device HTTP server that hosts the content can be stopped via the in-app control or the ongoing notification at any time. The port number that the HTTP server ends up with on each re-run is the one that the device's system provides of which is deemed unused at the time.

Android app permissions:

- INTERNET -- Listing network interfaces and running the HTTP server
- READ_EXTERNAL_STORAGE -- Read-only Direct Access Mode on internal storage (up to Android 10)
- FOREGROUND_SERVICE and FOREGROUND_SERVICE_CONNECTED_DEVICE -- Importing and sharing in the background
- POST_NOTIFICATIONS -- The sharing notification and the idle stop notice
- WAKE_LOCK -- Keeping the device awake during downloads
- CHANGE_WIFI_STATE -- Private hotspot (Android 13+), background sharing (Android 14+)
- NEARBY_WIFI_DEVICES -- Private hotspot (Android 13+); never used for location
- ACCESS_WIFI_STATE -- Checking hotspot capabilities (Android 13+)
- ACCESS_NETWORK_STATE -- Keeping the IP address list and QR code current

GitHub version (additional permissions):

- MANAGE_EXTERNAL_STORAGE -- Read-only Direct Access Mode on internal storage (Android 11+)
