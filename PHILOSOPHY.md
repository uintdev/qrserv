# QRServ's Design Philosophy

## Why are there very few features?

The idea of QRServ is to serve as a simple-to-use tool. It does what it says on the tin, with a simple user interface and experience.
<br>
With that in mind, it is fundamental to avoid deviating from that focus by adding features that may not fit satisfactorily, visually or otherwise. This can mean sacrificing some potential improvements if there is no clear and reasonable path forward that fits well within the aim.

In this case, the primary purpose is to make a file selection accessible through the connected network, then (optionally) scan the QR code with a device on the same network to obtain that file selection.

## Why does the app struggle with large files?

Android's document picker and share sheet only hand the app a content URI, not a real filesystem path-there is no way to read the original file directly from that. To work around this, the file picker has to copy the selected file into the app's own app data (cache) before it can be served. As a result, this temporarily uses more storage and adds the extra processing of making a copy of the selection. How fast this is depends on the SoC and NAND flash storage bandwidth. As you can imagine, this can be a struggle on lower-end devices.

It is worth noting that this limitation is not specific to this application. Any app relying on the document picker or share sheet for arbitrary files faces the same constraint.

Despite that, you can use Direct Access Mode to avoid the extra overhead (only one file can be selected at a time). This can be enabled in the app's settings. Please note that when using the share sheet to pass the file selection over, it uses the app cache method from the get-go, so DAM cannot be used in that case.
<br>
Due to Google Play restrictions in regard to Manage External Storage permission (required for direct file access on Android 11+), Direct Access Mode is only available for GitHub releases.

## Why does the HTTP server not offer a secure connection?

There was some debate about this concept, which several similar applications had adopted-some better than others (i.e., generated certificates and keys vs. hardcoded).
<br>
I have experience in software and web security and have pushed for better security. That said, it would be somewhat negligent to include the aforementioned functionality.

Right out of the gate, we would be talking about self-signed certificates. These inherently will not be trusted by clients that impose certificate validation checks. These clients are usually browsers.

QRServ does not offer a mode to specifically download files from another instance of QRServ. Other applications tend to do this, assuming that the client will be their own application rather than any other application they have no control over.

The main concern is encouraging users to skip certificate warnings. In general, there are man-in-the-middle risks before that self-signed certificate is temporarily trusted-hence the certificate warning in the first place.
<br>
I do not want to encourage such bad practices, nor do I wish to participate in security theater. It is not convenient or clear to the end user. It most certainly would therefore not be a good selling point. The bad outweighs the good. The solution has to be relatively solid.

If concerned about privacy and data integrity, use the private hotspot option (Android 13 or later). QRServ creates a Wi-Fi network of its own and serves the file only on it, so the file is only reachable by devices you give that network's details to (a QR code to scan, or the network name and password). The connection itself is still plain HTTP-anyone who has joined that network could in principle observe the traffic-which is why it is called private rather than secure.
<br>
Otherwise, consider using a trusted VPN that allows reachability of other VPN clients, a network that can be trusted, or mobile tethering (mobile data not required-it would offer a LAN, which is what you would need).
