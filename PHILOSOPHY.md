# QRServ's Design Philosophy

## Why are there very few features?

The idea of QRServ is to serve as a simple-to-use tool. It does what it says on the tin, with a simple user interface and experience.
<br>
With that in mind, it is fundamental to avoid deviating from that focus by adding features that may not fit satisfactorily, visually or otherwise. This can mean sacrificing some potential improvements if there is no clear and reasonable path forward that fits well within the aim.

In this case, the primary purpose is to make a file selection accessible through the connected network, then (optionally) scan the QR code with a device on the same network to obtain that file selection.

## Why does the app struggle with large files?

QRServ was built using the Flutter UI framework.

The file picker dependencies for the share sheet and the document UI do not allow gathering the original path and instead copy the selected file into cache. As a result, this temporarily uses more storage and adds the extra processing of making a copy of the selection. How fast this is depends on the SoC and NAND flash storage bandwidth. As you can imagine, this can be a struggle on lower-end devices.

It is worth noting that the limitations are not specific to this application. Similar applications built using Flutter experience such limitations as well.

Despite that, you can use Direct Access Mode to avoid the extra overhead (only one file can be selected at a time). This is the SD card icon at the top of the app. Please note that when using the share sheet to pass the file selection over, it uses the app cache method from the get-go, so DAM cannot be used in that case.
<br>
Due to Google Play restrictions, Direct Access Mode is only available for GitHub releases.

## Why does the HTTP server not offer a secure connection?

There was some debate about this concept, which several similar applications had adopted-some better than others (i.e., generated certificates and keys vs. hardcoded).
<br>
I have experience in software and web security and have pushed for better security. That said, it would be somewhat negligent to include the aforementioned functionality.

Right out of the gate, we would be talking about self-signed certificates. These inherently will not be trusted by clients that impose certificate validation checks. These clients are usually browsers.

QRServ does not offer a mode to specifically download files from another instance of QRServ. Other applications tend to do this, assuming that the client will be their own application rather than any other application they have no control over.

The main concern is encouraging users to skip certificate warnings. In general, there are man-in-the-middle risks before that self-signed certificate is temporarily trusted-hence the certificate warning in the first place.
<br>
I do not want to encourage such bad practices, nor do I wish to participate in security theater. It is not convenient or clear to the end user. It most certainly would therefore not be a good selling point. The bad outweighs the good. The solution has to be relatively solid.

If concerned about privacy and data integrity, consider using a trusted VPN that allows reachability of other VPN clients, a network that can be trusted, or mobile tethering (mobile data not required-it would offer a LAN, which is what you would need).
