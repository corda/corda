---------------------------
Purpose of LocalAttestation
---------------------------
The project demonstrates:
- How to establish a protected channel
- Secret message exchange using enclave to enclave function calls

------------------------------------
How to Build/Execute the Sample Code
------------------------------------
1. Install Intel(R) SGX SDK for Linux* OS
2. Build the project with the prepared Makefile:
    a. Hardware Mode, Debug build:
        $ make SGX_MODE=HW SGX_DEBUG=1
    b. Hardware Mode, Pre-release build:
        $ make SGX_MODE=HW SGX_PRERELEASE=1
    c. Hardware Mode, Release build:
        $ make SGX_MODE=HW
    d. Simulation Mode, Debug build:
        $ make SGX_DEBUG=1
    e. Simulation Mode, Pre-release build:
        $ make SGX_PRERELEASE=1
    f. Simulation Mode, Release build:
        $ make
3. Execute the binary directly:
    $ ./app

