<p align="center">
  <img src="https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png" alt="Corda" width="500">
</p>

# Corda

Corda is an open source blockchain project, designed for business from the start. Only Corda allows you to build interoperable blockchain networks that transact in strict privacy. Corda's smart contract technology allows businesses to transact directly, with value.

# Default branch

Master is no longer the default branch for Corda. New development work is committed to the latest release branch, which is named in the following format:
release/os/{major}.{minor}

Any new work should be committed to the earliest release branch for which the work is appropriate and then forward ported to newer branches.

# Example

You would like to fix an issue that is relevent to Corda 4.1 and 4.3. You create a Pull Request against release/os/4.1. Once this PR is merged, you would then have the PR forward merged from release/os/4.3

If the work is only relevant to Corda 4.3, it need not be merged into 4.1 first.