------------------------
Purpose of SampleEnclave
------------------------
The project demonstrates several fundamental usages of Intel(R) Software Guard 
Extensions (Intel(R) SGX) SDK:
- Initializing and destroying an enclave
- Creating ECALLs or OCALLs
- Calling trusted libraries inside the enclave

------------------------------------
How to Build/Execute the Sample Code
------------------------------------
1. Install Intel(R) SGX SDK for Linux* OS
2. Make sure your environment is set:
    $ source ${sgx-sdk-install-path}/environment
3. Build the project with the prepared Makefile:
    a. Hardware Mode, Debug build:
        $ make
    b. Hardware Mode, Pre-release build:
        $ make SGX_PRERELEASE=1 SGX_DEBUG=0
    c. Hardware Mode, Release build:
        $ make SGX_DEBUG=0
    d. Simulation Mode, Debug build:
        $ make SGX_MODE=SIM
    e. Simulation Mode, Pre-release build:
        $ make SGX_MODE=SIM SGX_PRERELEASE=1 SGX_DEBUG=0
    f. Simulation Mode, Release build:
        $ make SGX_MODE=SIM SGX_DEBUG=0
4. Execute the binary directly:
    $ ./app
5. Remember to "make clean" before switching build mode

------------------------------------------
Explanation about Configuration Parameters
------------------------------------------
TCSMaxNum, TCSNum, TCSMinPool

    These three parameters will determine whether a thread will be created
    dynamically  when there is no available thread to do the work.


StackMaxSize, StackMinSize

    For a dynamically created thread, StackMinSize is the amount of stack available
    once the thread is created and StackMaxSize is the total amount of stack that
    thread can use. The gap between StackMinSize and StackMaxSize is the stack
    dynamically expanded as necessary at runtime.

    For a static thread, only StackMaxSize is relevant which specifies the total
    amount of stack available to the thread.


HeapMaxSize, HeapInitSize, HeapMinSize

    HeapMinSize is the amount of heap available once the enclave is initialized.

    HeapMaxSize is the total amount of heap an enclave can use. The gap between
    HeapMinSize and HeapMaxSize is the heap dynamically expanded as necessary
    at runtime.

    HeapInitSize is here for compatibility.

-------------------------------------------------    
Sample configuration files for the Sample Enclave
-------------------------------------------------
config.01.xml: There is no dynamic thread, no dynamic heap expansion.
config.02.xml: There is no dynamic thread. But dynamic heap expansion can happen.
config.03.xml: There are dynamic threads. For a dynamic thread, there's no stack expansion.
config.04.xml: There are dynamic threads. For a dynamic thread, stack will expanded as necessary.
