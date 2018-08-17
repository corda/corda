
--------------------------------------------
Design Decision: Should the check for minPlatformVersion be performed in the CorDapp as well as in the Node?
============================================

## Background / Context

Nodes running a version lower than the one that will include the changes proposed here will run CorDapps with a `minPlatformVersion` higher than their platform version. 
To prevent this, the check for `minPlatformVersion` could be performed in the CorDapp as well as in the node. 
When a CorDapp detects that a node which is not fulfilling its `minPlatformVersion` requirement is attempting to run it, what should happen?
Should the check for minPlatformVersion be performed in the CorDapp as well as in the Node?

## Options Analysis

### A. Only check in the Node

#### Advantages

1.    ​
2.    ​

#### Disadvantages

1.    ​
2.    ​

### B. Perform the check for minPlatformVersion in the CorDapp and the Node

#### Advantages

1.  Older nodes can be prevented from running a CorDapp that requires a newer platform version. ​

#### Disadvantages

1. ​
2. ​

## Recommendation and justification

Proceed with Option <A or B or ... > 
