![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

--------------------------------------------
Design Decision: How should the node react when the check for minimum or target version fails?
============================================

## Background / Context

What should happen when the node loads a CorDapp and detects that the CorDapp's minimum platform version is greater than the node's platform version, or the CorDapps target platform version is less than the network's minimum target version? 

## Options Analysis

### A. The node does not load the CorDapp and logs a warning, but resumes normal operation.

#### Advantages

1.    ​
2.    ​

#### Disadvantages

1.  It is possible that node operators miss these warnings, leading to problems in production.
2.    ​

### B. The node logs an error and shuts down.

#### Advantages

1. ​Potential problems will be spotted earlier
2. ​

#### Disadvantages

1. ​
2. ​

## Recommendation and justification

Use either A or B as default, make the behaviour configurable.