# SGX Build Container and Utilities

## Project Organisation

  * **Containers**

    To pin down dependencies and simplify development and testing, we have a
    Docker image with all necessary compile- and run-time dependencies
    pre-installed. This image supports mounting of volumes for the user's home
    directory, code repository, and SGX SDK directory. It also exposes various
    ports for debuggable targets (JVM and native). To run SGX-enabled
    applications in hardware mode, the user must pass in a reference to the SGX
    kernel driver (which is done automagically if the `sx` command is used).

  * **Tools**

    `sx` is a utility that simplifies running builds and tests inside the SGX
    container, and also provides some additional helper functions for things
    like generating tags databases, starting debug servers, etc.


## Getting Started

To get started, run the following commands in `sgx-jvm`:

```bash
> source environment
> sx help
```
Yielding the following output:

```
usage: <variables> sx <command> <options>

<command>
  build                build project in container (<directory> <arguments>)
  containers           actions related to containers
  debug                actions related to debugging
  exec                 shorthand for `containers exec core`
  get-started          build containers and key components
  help                 show help information
  hsm                  actions related to the hsm simulator
  logs                 tail application logs
  reports              actions related to reports
  shell                show information about shell commands
  tags                 actions related to tag databases

<options>
  -c                   colours = on | off (-C)
  -d                   debug = on | off (-D)
  -f                   force operation
  -h                   hardware = on | off (-s)
  -r                   target = release | pre-release (-p)
  -s                   hsm profile = simulator | development hsm (-S) | production (-P)
  -t                   tty = on | off (-T)
  -v                   verbose output

<variables>
  LINES                number of lines to return from the end of the log files (default 50)
  PORT                 port number used for connecting to the ISV (default 9080)
```

The first command simply sets up an alias pointing to `sgx-jvm/tools/sx/sx`,
and enables Bash auto-completion for the various command options. For example:

```bash
> sx b<tab> # will expand to "sx build"
```

The second command shows all the available sub-commands and options.

If this is your first time using `sx`, you will most likely have to build the
Docker container used for building and running the various components of the
SGX projects. To do that, run the command:

```bash
> sx get-started
```

This command will also set up default configuration for SGX-GDB, both inside
and outside of the container, and Visual Studio Code configuration if you fancy
running remote debugging sessions from an IDE.

## Building Components

As an example, this section will go through the process of building the various
components of the remote attestation project.

### Enclave

To build the enclave and sign it with a self-signed OpenSSL certificate (for
testing), run the following command:

```bash
> sx build remote-attestation/enclave clean all
```

This command runs `make -C sgx-jvm/remote-attestation/enclave clean all` inside
the SGX container.

To build the enclave in hardware and pre-release mode, use the `-h` and `-p`
switches like this:

```bash
> sx build -hp remote-attestation/enclave clean all
```

### Host

Similarly, to build the host (JVM-layer), you can run the following command:

```bash
> sx build remote-attestation/host
```

This will run `gradlew` in the `host/` directory, with the necessary paths and
environment variables set.

### JNI Library

This is a native library, so you can compile it either for use with software
simulation or hardware.

```bash
> sx build remote-attestation/host/native       # simulation, debug mode
# or:
> sx build -hp remote-attestation/host/native   # hardware, pre-release mode
```

As part of the build, as seen in `host/native/Makefile`, we run `javah` on the
`NativeWrapper` class to extract its JNI mapping. This mapping will be written
to `wrapper.hpp`. This means that the JVM-layer needs building _prior_ to this
step.

## Running and Debugging Components

### Unit Tests

The unit tests are run through Gradle inside the SGX container, with the
various paths set to necessary dependencies. For instance, we need to set the
`java.library.path` and `corda.sgx.enclave.path` variables to point to the JNI
library and the enclave shared object, respectively. This is all done for you
by the Gradle build script, the container, and the `sx` tool.

Provided that you have built the aforementioned components, you can now run the
unit tests with the following command:

```bash
> sx build remote-attestation/host unit-tests
```

You can open the output report by issuing the following command:

```bash
> sx reports unit-tests
```

### Integration Tests

Similarly, you can run the integration tests with the following command:

```bash
> sx build remote-attestation/host integration-tests
```

This requires that the service provider (in the future challenger and IAS
proxy) is running. Say that the service is running on port 12345, you can run
the tests like this:

```bash
> PORT=12345 sx build remote-attestation/host integration-tests
```

You can open the output report by issuing the following command:

```bash
> sx reports integration-tests
```

If you want to explore the logs, you can use the `logs` command:

```bash
> LINES=100 sx logs
```

### Test Flow

There is also a simple attestation flow which similarly to the integration test
requires the service provider to run on a specific port. This flow can be run
with the `sx build` command.

To run the simple flow without attaching a debugger, run:

```bash
> PORT=8080 sx build remote-attestation/host run
```

There are a few different debug targets depending on how you want to run your
debugger:

  * **Local**

    Runs `gdb` inside the Docker container (if you don't have `gdb`
    installed on your computer): `run-local`.

  * **Remote**

    Runs `gdbserver` inside the Docker container so that you can attach to it
    from the host computer or another machine: `run-remote`.

  * **SGX**

    Runs `sgx-gdb` inside the Docker container (if you don't have `sgx-gdb`
    installed on your computer): `run-sgx`. This lets you step through
    enclave-code, inspect stack traces in the trusted environment, etc.
    Obviously, this is only possible if the program has been compiled for
    debug and simulation mode.

For all of the above, and for the unit and integration tests, you can attach a
Java debugger remotely as well, using JDWP.

## Other Tools

### CTags

For the C/C++ part of the project, you might wish to construct a tags file to
easily jump back and forth between symbols. You can construct this either with
or without the symbols from the Linux SGX SDK:

```bash
> sx tags lean remote-attestation # Remote Attestation project only
> sx tags full remote-attestation # Include symbols from the SGX SDK
```

## Dependencies

 * **Intel SGX SDK** – [01org/linux-sgx](https://github.com/01org/linux-sgx)
 * **Intel SGX Driver** – [01org/linux-sgx-driver](https://github.com/01org/linux-sgx-driver)
