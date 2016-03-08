Node administration
===================

When a node is running, it exposes an embedded web server that lets you monitor it, upload and download attachments,
access a REST API and so on.

Uploading and downloading attachments
-------------------------------------

Attachments are files that add context to and influence the behaviour of transactions. They are always identified by
hash and they are public, in that they propagate through the network to wherever they are needed.

All attachments are zip files. Thus to upload a file to the ledger you must first wrap it into a zip (or jar) file. Then
you can upload it by running this command from a UNIX terminal:

.. sourcecode:: shell

   curl -F myfile=@path/to/my/file.zip http://localhost:31338/upload/attachment

The attachment will be identified by the SHA-256 hash of the contents, which you can get by doing:

.. sourcecode:: shell

   shasum -a 256 file.zip

on a Mac or by using ``sha256sum`` on Linux. Alternatively, the hash will be returned to you when you upload the
attachment.

An attachment may be downloaded by fetching:

.. sourcecode:: shell

   http://localhost:31338/attachments/DECD098666B9657314870E192CED0C3519C2C9D395507A238338F8D003929DE9

where DECD... is of course replaced with the hash identifier of your own attachment. Because attachments are always
containers, you can also fetch a specific file within the attachment by appending its path, like this:

.. sourcecode:: shell

   http://localhost:31338/attachments/DECD098666B9657314870E192CED0C3519C2C9D395507A238338F8D003929DE9/path/within/zip.txt

