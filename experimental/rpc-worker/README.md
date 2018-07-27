# Introduction

This module attempts to break RPC connectivity bit away from Corda Node such that it will be possible to run it as separate
operating system process.

This will part of so called clustered Node set of services each of which will speak to its siblings via remote protocol and
those services will have different life cycles availability guarantees. 
