# QRServ Privacy Policy

QRServ does not collect or send any information that might be personal or personally identifiable. There is no in-app data collection.

QRServ hosts content that the user specifically selects and that selection is then made available to any network the device being utilised is connected to. This could be on a Local Area Network (including mobile tethering and common access points), Wide Area Network (depending on the network configuration) and/or a connected VPN.
QRServ's on-device HTTP server that hosts the content can be stopped via the in-app control at any time. The port number that the HTTP server ends up with on each re-run is the one that the device's system provides of which is deemed unused at the time.

Android app permissions:

-   android.permission.INTERNET -- Collection of available network interfaces and port binding for the HTTP server
-   android.permission.READ_EXTERNAL_STORAGE -- Read-only access to emulated, physical SD card(s) and USB mass storage

GitHub version (additional permissions):

-   android.permission.MANAGE_EXTERNAL_STORAGE -- Direct access to select files on internal storage rather than what gets cached first
