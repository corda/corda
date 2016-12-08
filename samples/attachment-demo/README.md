# Attachment Demo 

This code demonstrates sending a transaction with an attachment from one to node to another, and the receiving node accessing the attachment.

Please see the either the [online documentation](https://docs.corda.net/running-the-demos.html#attachment-demo) for more info on the attachment demo, or the [included offline version](../../docs/build/html/running-the-demos.html#attachment-demo).

From the root directory of the repository, run the following commands (on mac / unix, replace `gradle` with `./gradlew`)

    gradle samples:attachment-demo:deployNodes 

    ./samples/attachment-demo/build/nodes/runnodes

    gradle samples:attachment-demo:runRecipient  # (in one window)
    gradle samples:attachment-demo:runSender  # (in another window)