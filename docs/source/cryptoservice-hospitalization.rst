HSM exception support in Flow Hospital
======================================

It is very likely that under certain circumstances exceptions will be raised from either the HSM directly or the corresponding CryptoService
interface. Some of these exceptions are recoverable and will be managed by the Flow Hospital.  Below is a list of the current
exceptions which are retried.

In these cases the Flow Hospital will attempt a retry of the flow.  The hospital will retry a number of times before giving up.
At this point, it will let the exception propagate and the flow will terminate.

Recoverable Exceptions
----------------------

The following exceptions will be considered by the Flow Hospital for retry:

    TimedCryptoServiceException

