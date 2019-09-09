<p align="center">
  <img src="https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png" alt="Corda" width="500">
</p>

# Corda

Corda is an open source blockchain project, designed for business from the start. Only Corda allows you to build interoperable blockchain networks that transact in strict privacy. Corda's smart contract technology allows businesses to transact directly, with value.

# Contributing

Corda is an open-source project and contributions are welcome! Please note that we do not use the master branch. Please see below for more detail.

To find out how to contribute, please see our [contributing docs](https://docs.corda.net/head/contributing-index.html).

# Development of Corda going forward 
 
Previously, all development work was done on master. Master will no longer be used, owing to the need to support multiple versions of Corda in flight at any time. Instead, development work will be done on branches that represent the appropriate version. Branches will be named according to the following pattern:
release/os/{major version}.{minor version}

The default branch will track the current version of Corda in development and is expected to change over time as new versions of Corda are released.
 
# Where should I make my change?
 
If you would like to contribute to Corda, we recommend opening a pull request against the oldest version of Corda to which your work would apply. For instance, if your work would be applicable to Corda 4.1 and Corda 4.3, it would be appropriate to open a pull request for release/os/4.1. That work would then be merged forward from release/os/4.1 to release/os/4.3. If your work is only applicable to Corda 4.3, simply open a pull request against release/os/4.3.
 
# Why was this change made?
 
The previous branching structure required changes to be made on master first and then backported to older versions. However, backporting was proving to be complicated and time consuming. In order to streamline development, it was decided that forward merging would be used instead, which required moving away from development based around a master branch.

Please see Mike Ward's [blog post](https://medium.com/corda/corda-release-versioning-changes-6281b02348fc) for more detail on why the versioning strategy was changed.
