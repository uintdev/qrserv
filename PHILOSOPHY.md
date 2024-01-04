# QRServ's Design Philosophy

## Why are there very few features?

The idea of QRServ is to serve as a simple-to-use tool. It does what it says on the tin, with a simple user interface and experience.
<br>
With that in mind, it is therefore fundamental to avoid deviating from that focus by adding features that may not fit in a way that is satisfactory. Visually or otherwise. This can mean sacrificing some potential improvements if there is no clear and reasonable path forward that fits well within the aim.

In this case, the primary purpose is to simply make a file selection accessible through the connected network, to then (optionally) scan the QR code with a device that is on the same network to obtain said file selection.

## Why does the app struggle with large files?

QRServ was built using the Flutter UI framework.

The file picker dependencies for the share sheet and the document UI would not allow gathering of the original path and instead would copy the selected file into cache. As a result, this temporarily uses up more storage and adds the extra processing of making a copy of the selection. How fast this would be depends on the SoC and NAND flash storage bandwidth. As you could imagine, this can especially be a struggle on lower end devices.

It is worth noting that the limitations are not limited to this application in particular. Similar applications built using Flutter experience such limitations as well.

Despite all that, you can use Direct Access Mode to avoid all that mess (only one file can be selected at a time). This will be the SD card icon on top of the app. Please note that when using the share sheet to pass the file selection over, it uses the app cache method from the get-go and so DAM simply can't be used in that case.

## Why does the HTTP server not offer a secure connection?

There was a bit of a debate regarding this concept that several other similar applications had adopted. Some better than others (i.e. generated certificate & keys vs. hardcoded).
<br>
I happen to have experience in software and web security, and have pushed for better security. With that said, it would feel rather negligent to include the aforementioned functionality.

Right out the gate, we would be talking about self-signed certificates. These inherently will not be trusted by clients that impose certificate validation checks. These clients would usually be browsers.

The main concern is essentially encouraging users to skip certificate warnings. In general, there are going to be man-in-the-middle risks before that self-signed certificate is temporarily trusted -- hence the certificate warning in the first place.
<br>
I do not want to encourage such bad practices, nor do I wish to participate in security theatre. It is not convenient or concise to the end user, nor is it an appropriate selling point bearing the aforementioned in mind. The bad outweighs the good. The solution has to be relatively solid.

If concerned for privacy and data integrity, I would encourage trying using a trusted VPN that allows for reachability of other VPN clients, a network that can be trusted, or mobile tethering (mobile data not required -- just needs a 'local' network, as standard).
