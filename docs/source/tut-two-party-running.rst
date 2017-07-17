Running our CorDapp
===================



Conclusion
----------
We have written a simple CorDapp that allows IOUs to be issued onto the ledger. Like all CorDapps, our
CorDapp is made up of three key parts:

* The ``IOUState``, representing IOUs on the ledger
* The ``IOUContract``, controlling the evolution of IOUs over time
* The ``IOUFlow``, orchestrating the process of agreeing the creation of an IOU on-ledger.

Together, these three parts completely determine how IOUs are created and evolved on the ledger.

Next steps
----------
You should now be ready to develop your own CorDapps. There's
`a more fleshed-out version of the IOU CorDapp <https://github.com/corda/cordapp-tutorial>`_
with an API and web front-end, and a set of example CorDapps in
`the main Corda repo <https://github.com/corda/corda>`_, under ``samples``. An explanation of how to run these
samples :doc:`here <running-the-demos>`.

As you write CorDapps, you can learn more about the API available :doc:`here <api>`.

If you get stuck at any point, please reach out on `Slack <https://slack.corda.net/>`_,
`Discourse <https://discourse.corda.net/>`_, or `Stack Overflow <https://stackoverflow.com/questions/tagged/corda>`_.