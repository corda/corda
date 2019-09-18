# Handle attachments that don't contain code

Clients wish to attach transaction specific files to transactions.
These files might contain data used for transaction verification, or more likely just be some data attached as a reference. 

There are a couple problems that this causes in the current implementation of Corda (these were known): 

1. Having a per-transaction specific file attached renders the ``AttachmentsClasssloader`` caching mechanism useless. 
2. Reference files are subject to the the no-overlap rule. This causes pain to clients who wish to attach such files.
3. Attached files must not exceed: ``(maxTransactionSize - restOfTheTransaction)``   

To solve 3., we must implement the ``Data streams`` approach described in the refreshed TWP.

This design proposes a few solutions for 1. and 2. Each has tradeoffs. 


### Attachment types
 
There are 3 classes of attachments:
 - attachments that are ``code`` - referred as ``CodeAttachment``
 - attachments that are ``consensus data`` - referred as ``DataAttachment`` 
 - attachments that are ``reference data`` - referred as ``ReferenceDataAttachment``

#### Properties of these attachment types:

##### ``CodeAttachment``
- these attachments will contain class files and resources. 
- must be in the ``AttachmentsClassloader`` where the contract verification runs.
- the code attachments combinations used in transactions should generally be similar and are a good candidate for caching. (e.g.: Apples generally used with Oranges) 
- when added to the transaction classloader, they must obey the "no-overlap" rule.
- these attachments need to be scanned for custom serializers.  
- these attachments are either constrained by an ``AttachmentConstraint`` or are a dependency.


##### ``DataAttachment``
- they contain no code (class files). 
- the data files in these attachments are needed by the contract. E.g.: holiday calendars.
- these attachments can be unique per transaction. Given this property, they should not be part of the classloader caching mechanism.

Question: Should these be available as classloader resources?
 (If yes, then these files must also obey the no-overlap rule.) 


##### ``ReferenceDataAttachment``
- these are files that are not required by the contract. Mike described their properties in the ``Data streams`` section described in the refreshed TWP.
- This design does not cover these. Any client who wants to use this type of attachments must use the described approach (tldr: add the document hashes to the output state, and upload the files to a CDN.) 


### Decision 1: Given that there is no api, how can we differentiate between ``CodeAttachment``s and ``DataAttachment``s?

They are added using the ``TransactionBuilder.addAttachment``, and at runtime, the only thing available is the ``InpuStream`` of the attachment.

#### Option a.
Just scan each archive, and if a ``.class`` file is found then it is a ``CodeAttachment``, otherwise a ``DataAttachment``.

#### Option b.
We create a rule that states:
- ``CodeAttachment``s must always be JAR files. And all JAR files must have a manifest.
- ``DataAttachment``s must always be ZIP files. ZIP files will not have a manifest.

For the proposed solution of this design we choose Option b.
 

### Decision 2: Should ``DataAttachment``s be available on the transaction Classloader?

The drawback of having them on the classloader is that they must obey the no-overlap rule. 
Which seems to cause issues for some customers.

The drawback of not having them on the classloader is that we break backwards compatibility with Corda 4 - where they were available? 
This was not a feature we supported in version 3 either.
They can still be accessed from the ``LedgerTransaction.attachments`` 

For the proposed solution of this design we choose to add the ``DataAttachment``s to the Transaction classloader.


## Proposed solution

Before creating the Transaction classloader split the attachments into the 2 categories.
Create an ``AttachmentsClassloader`` from the ``CodeAttachment``s and scan for custom serializers and whitelisted classes.
The above will be cached keyed on the set of ``CodeAttachment`` ids.

If there are any ``DataAttachment``s, create another ``AttachmentsClassloader`` that has the above as the parent and contains all data attachments.

With this approach, if a resource from a  ``DataAttachment`` overlaps a resource from a ``CodeAttachment`` it will be ignored.
 

