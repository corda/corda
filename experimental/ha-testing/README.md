# Introduction

This module provides a facility to perform High Availability(HA) testing of Corda Nodes.
It assumes that there is an environment running on remote hosts to which it is possible
communicate via RPC. Also, assumption is made that Finance CorDapp is installed on the nodes. 

# Testing principle

The test script running on a local machine and communicates to remote nodes.
There are two nodes in scope:
* Normal node - a stable node that is meant to remain available 100% of the time during test script execution;
* HA node - which may have windows of instability through scenario execution, but meant to recover 
and continue serving RPC requests and interact with its peers via P2P.

Main method of this module performs the following actions:
1. Parses incoming parameters;
2. Establishes RPC connection to Normal and HA nodes;
3. Performs a series of flow postings;
4. Whilst flow postings running, triggers termination of the HA node cluster to simulate disaster scenario
(**Note:** This is a target/future state, subject to: https://r3-cev.atlassian.net/browse/ENT-1967 being completed first. 
At the time of writing disaster scenario will have to be simulated by the operator manually.);
5. When RPC connection to HA Node is lost, re-establishes RPC communication and attempts to drive test scenario to the end;
6. Once all the postings successfully processed, performs validation of the transactions/states to ensure consistency;
6. Returns exit code of `0` in case success or `1` in case of a failure.  

At the moment multi-threaded aspect of testing is not in scope and all the testing performed from a single thread from
test script side. This means that at any one time there can only be a **single** transaction "in flight". 

# Running test script

Main method has the following parameters:

| Option                                   | Description |
| ------                                   | ----------- |
| --normalNodeRpcAddress <<host:port>>     | Normal Node RPC address |             
| --normalNodeRpcUserName <free_form_text> | Normal Node RPC user name |
| --normalNodeRpcPassword <free_form_text> | Normal Node RPC password |                               
| --haNodeRpcAddress <<host:port>>         | High Available Node RPC address |
| --haNodeRpcUserName <free_form_text>     | High Available Node RPC user name |  
| --haNodeRpcPassword <free_form_text>     | High Available Node RPC password |             
| --scenarioType <free_form_text>          | Type of scenario to run. Currently supported values: `Cash`, `LinearState` |
| --iterationsCount [positive_integer]     | Number of iteration to execute |