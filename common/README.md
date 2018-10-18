# Common libraries

This directory contains modules representing libraries that are reusable in different areas of Corda.

## Rules of the folder

- No dependencies whatsoever on any modules that are not in this directory (no corda-core, test-utils, etc.).
- No active components, as in, nothing that has a main function in it.
- Think carefully before using non-internal packages in these libraries.