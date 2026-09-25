# QRServ Privacy Policy

QRServ does not collect or send any information that might be personal or personally identifiable. There is no in-app data collection.

QRServ hosts content that the user specifically selects and that selection is then made available on the network of the IP address selected in the app, or on every network the device being utilized is connected to if listening on all interfaces is enabled in settings. This could be on a Local Area Network (including mobile tethering and common access points), Wide Area Network (depending on the network configuration) and/or a connected VPN.
When the private hotspot option is used (Android 13 or later), QRServ instead creates a Wi-Fi network of its own and the selection is made available only on that network, to devices that are given its network name and password.
QRServ checks on the device whether Android's "Block connections without VPN" setting is on, only to warn that other devices may be blocked from connecting. This is not stored or sent anywhere.
QRServ's on-device HTTP server that hosts the content can be stopped via the in-app control or the ongoing notification at any time. The port number that the HTTP server ends up with on each re-run is the one that the device's system provides of which is deemed unused at the time.

Android app permissions:

-   android.permission.INTERNET -- Collection of available network interfaces and port binding for the HTTP server
-   android.permission.READ_EXTERNAL_STORAGE -- Read-only access to emulated, physical SD card(s) and USB mass storage
-   android.permission.FOREGROUND_SERVICE and android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE -- Keeping the HTTP server (and private hotspot) running while the app is in the background
-   android.permission.POST_NOTIFICATIONS -- The ongoing notification shown while a file is being shared, with the option to stop sharing
-   android.permission.WAKE_LOCK -- Keeping the device awake while a download is in progress, so it isn't stalled by the screen turning off
-   android.permission.CHANGE_WIFI_STATE -- Creating and stopping the private hotspot
-   android.permission.NEARBY_WIFI_DEVICES -- Creating the private hotspot (Android 13 or later). Not used to find or locate devices
-   android.permission.ACCESS_WIFI_STATE -- Checking whether the device can host a hotspot while staying connected to Wi-Fi
-   android.permission.ACCESS_NETWORK_STATE -- Noticing when the device's IP addresses change while sharing, so the QR code can follow

GitHub version (additional permissions):

-   android.permission.MANAGE_EXTERNAL_STORAGE -- Direct access to select files on internal storage rather than what gets cached first
