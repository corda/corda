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
 
There are 4 classes of attachments:
 - attachments that are ``code`` - referred as ``CodeAttachment``. 
 - attachments that are ``consensus data`` - referred as ``DataAttachment`` 
 - attachments that are ``reference data``, small in size - referred as ``SmallReferenceDataAttachment``
 - attachments that are ``reference data``, large in size - referred as ``LargeReferenceDataAttachment``


### Properties of the attachment types:

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
- these attachments should be treated like dependencies, because the result of transaction verification depends on them. This means that they must be signable or the contract must hardcode their hash.
- these attachments are unlikely to be unique per transaction, given that they must be constrained.


##### ``SmallReferenceDataAttachment``
- these are files that are not required by the contract. 
- they are just files that are relevant to this transaction. 
- these attachments are unique per transaction, and do naturally not obey the "no-overlap" rule. 
 

##### ``LargeReferenceDataAttachment``
- these are files that are not required by the contract. Mike described their properties in the ``Data streams`` section described in the refreshed TWP.
- This design does not cover these. Any client who wants to use this type of attachments must use the described approach (tldr: add the document hashes to the output state, and upload the files to a CDN.) 


Given that ``CodeAttachment``s and ``DataAttachment``s are similar in nature, there is no reason to differentiate between them.
They will be referred to as ``ConsensusAttachment``s. 


As a conclusion, we can classify the attachments in:
  - Consensus Attachments :  ``CodeAttachment`` and ``DataAttachment``.
  - Reference data attachments : ``SmallReferenceDataAttachment`` and ``LargeReferenceDataAttachment``.


### Transaction verification and attachments

Given that ``DataAttachment``s are unlikely to be unique per transaction they can be on the base ``AttachmentsClassloader`` and must obey the no-overlap rule.
They can also be cached together with the ``CodeAttachment``s.

``SmallReferenceDataAttachment``, on the other hand, cannot be on the base ``AttachmentsClassloader`` because they are unique per transaction, and also can't obey to the no-overlap rule. 


### Given that there is no API, how can we differentiate between ``ConsensusAttachment``s and ``SmallReferenceDataAttachment``s ?

They are added using the ``TransactionBuilder.addAttachment``, and at runtime the only thing available is the ``InpuStream`` of the attachment.
When we add ``LargeReferenceDataAttachment``s , we'll add a new API.

#### Solution:

We create a rule that states:
- ``ConsensusAttachment``s must always be JAR files. And all JAR files must have a manifest.
- ``SmallReferenceDataAttachment``s must always be ZIP files. ZIP files will not have a manifest.

Note: It is not possible to just scan each archive, and look for ``.class`` files because ``DataAttachment`` will not contain any classes either.  


### Should the ``SmallReferenceDataAttachment``s be on the transaction classloader?

We know that they are ``ZIP`` files and that their content does not obey the no-overlap rule.
Also, there is no reason for contract code to attempt to open them, because they are not consensus critical.

The answer is: ``No - they should *not* be on the transaction classloader``.


## Proposed solution

Before creating the Transaction classloader we must split the attachments into the 2 categories: ``ConsensusAttachment`` and ``SmallReferenceDataAttachment``.

Then, create an ``AttachmentsClassloader`` from the ``ConsensusAttachment``s and scan for custom serializers and whitelisted classes.
The above will be cached keyed on the set of ``ConsensusAttachment`` ids.

This solves the problems that clients have if they just attach the small reference files as `ZIP`s.