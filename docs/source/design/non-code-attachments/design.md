# Improve support for attachments that don't contain code

The issue we're currently facing is that clients wish to attach transaction specific data files to transactions.
These files might contain data used for transaction verification, or more likely just be some data attached as a reference. 

There are a couple problems that this requirement causes in the current implementation of Corda: 

1. Having a per-transaction specific file attached renders the ``AttachmentsClasssloader`` caching mechanism useless, which significantly impacts performance. 
   Without the caching, we are seeing clients take 3 seconds to create a transaction due to the scan for custom serializers making it's way through large ZIP files.
2. Reference files are subject to the the no-overlap rule. This causes pain to clients who wish to attach archives of user uploaded content.
3. Attached files must not exceed: ``(maxTransactionSize - restOfTheTransaction)``   

Note: 1. and 2. were not problems in Corda 3.x because there was no ``AttachmentsClasssloader``.

To solve 3., we must implement the ``Data streams`` approach described in the refreshed TWP.

This design proposes a solutions for 1. and a potential solution for 2. . 


### Attachment types
 
There are 3 classes of attachments:
 - attachments that are ``code`` - referred as ``CodeAttachment``. 
 - attachments that are ``consensus data`` - referred as ``DataAttachment`` 
 - attachments that are ``reference data`` - referred as ``ReferenceDataAttachment``

#### Properties of these attachment types:

##### ``CodeAttachment``
- these attachments will contain class files and resources. 
- must be in the ``AttachmentsClassloader`` where the contract verification runs.
- the code attachments combinations used in transactions should generally be similar and are a good candidate for caching. (e.g.: Apples generally used with Oranges) 
- when added to the transaction classloader, they must obey the "no-overlap" rule.
- these attachments need to be scanned for custom serializers and whitelisted classes.  
- these attachments are either cryptographically constrained by an ``AttachmentConstraint`` or are a dependency of a contract file.


##### ``DataAttachment``
- they contain no code. 
- the data files in these attachments are needed by the contract. E.g.: holiday calendars.
- these attachments can be unique per transaction. Given this property, they should not be part of the classloader caching mechanism.

Question: Should these be available as classloader resources?
 

##### ``ReferenceDataAttachment``
- these are files that are not required by the contract. Mike described their properties in the ``Data streams`` section described in the refreshed TWP.
- This design does not cover these. Any client who wants to use this type of attachments must use the described approach (tldr: add the document hashes to the output state, and upload the files to a CDN.) 


### Decision 1: Given that there is no api, how can we differentiate between ``CodeAttachment``s and ``DataAttachment``s?

They are added using the ``TransactionBuilder.addAttachment``, and at runtime the only thing available is the ``InpuStream`` of the attachment.

#### Option a.
Just scan each archive, and if a ``.class`` file is found then it is a ``CodeAttachment``, otherwise a ``DataAttachment``.

#### Option b.
We create a rule that states:
- ``CodeAttachment``s must always be JAR files. And all JAR files must have a manifest.
- ``DataAttachment``s must always be ZIP files. ZIP files will not have a manifest.

Note: For the proposed solution of this design we choose Option b.
 

### Decision 2: Should ``DataAttachment``s be available on the transaction Classloader?

The drawback of having them in the transaction classloader is that they must obey the no-overlap rule. 
This should not be a problem for ``DataAttachment``s.
From client feedback it seems that duplicate file names is a problem for ``ReferenceDataAttachment``, for which an alternative mechanism must be used.

The drawback of not having them on the classloader is that we break backwards compatibility with Corda 4 - where they were available. 
Note that this was not a feature we supported in version 3 either, and they can still be accessed from the ``LedgerTransaction.attachments`` 

Note: For the proposed solution of this design we choose to add the ``DataAttachment``s to the Transaction classloader.


## Proposed solution

Before creating the Transaction classloader we must split the attachments into the 2 categories.

Then, create an ``AttachmentsClassloader`` from the ``CodeAttachment``s and scan for custom serializers and whitelisted classes.
The above will be cached keyed on the set of ``CodeAttachment`` ids.

If there are any ``DataAttachment``s, create another ``AttachmentsClassloader`` that has the above as the parent and contains all data attachments.

With this approach, if a resource from a  ``DataAttachment`` overlaps a resource from a ``CodeAttachment`` it will be ignored.
 
The transaction will be deserialized and verified in this child classloader, which will make the behaviour equivalent to Corda 4.

Advantages:
  - Caching the classloader and the scanned custom serializers will be efficient.
  - No functional change from Corda 4.
  
Disadvantages:
  - This doesn't solve the ``ReferenceDataAttachment`` issues. The size of the attachments must be low, and the names unique.    
  - Based on the ``Decision 1``, we must formulate and document a new rule.