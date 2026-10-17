# QRServ Privacy Policy

QRServ does not collect or send any information that might be personal or personally identifiable. There is no in-app data collection.

QRServ hosts content that the user specifically selects and that selection is then made available on the network of the IP address selected in the app, or on every network the device being utilized is connected to if listening on all interfaces is enabled in settings. This could be on a Local Area Network (including mobile tethering and common access points), Wide Area Network (depending on the network configuration) and/or a connected VPN.
When the private hotspot option is used (Android 13 or later), QRServ instead creates a Wi-Fi network of its own and the selection is made available only on that network, to devices that are given its network name and password.
QRServ checks on the device whether Android's "Block connections without VPN" setting is on, only to warn that other devices may be blocked from connecting. This is not stored or sent anywhere.
QRServ's on-device HTTP server that hosts the content can be stopped via the in-app control or the ongoing notification at any time. The port number that the HTTP server ends up with on each re-run is the one that the device's system provides of which is deemed unused at the time.

Android app permissions:

- INTERNET -- Listing network interfaces and binding the HTTP server's port
- READ_EXTERNAL_STORAGE -- Read-only access to internal storage, SD cards and USB drives
- FOREGROUND_SERVICE and FOREGROUND_SERVICE_CONNECTED_DEVICE -- Keeping the server (and private hotspot) running in the background
- POST_NOTIFICATIONS -- The ongoing sharing notification, with the option to stop sharing
- WAKE_LOCK -- Keeping the device awake while on the file sharing screen
- CHANGE_WIFI_STATE -- Starting and stopping the private hotspot
- NEARBY_WIFI_DEVICES -- Creating the private hotspot (Android 13 or later). Not used to locate devices
- ACCESS_WIFI_STATE -- Checking if the device can host a hotspot while staying on Wi-Fi
- ACCESS_NETWORK_STATE -- Noticing IP address changes while sharing, so the QR code can follow

GitHub version (additional permissions):

- MANAGE_EXTERNAL_STORAGE -- Direct access to selected files on internal storage instead of caching them first (only outside Google Play)
