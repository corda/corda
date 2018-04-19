# Introduction

This project illustrates how one can use Cucumber / BDD to drive
and test homogeneous and heterogeneous Corda networks on a local
machine. The framework has built-in support for Dockerised node
dependencies so that you easily can spin up a Corda node locally
that, for instance, uses a 3rd party database provider such as
Postgres.

# Structure

The project is split into three pieces:

 * **Testing Library** (main) - This library contains auxiliary
   functions that help in configuring and bootstrapping Corda
   networks on a local machine. The purpose of the library is to
   aid in black-box testing and automation.

 * **Unit Tests** (test) - These are various tests for the
   library described above. Note that there's only limited
   coverage for now.

 * **BDD Framework** (scenario) - This module shows how to use
   BDD-style frameworks to control the testing of Corda networks;
   more specifically, using [Cucumber](cucumber.io).

# Setup

To get started, please follow the instructions below:

 * Go up to the root directory and build the capsule JAR.

    ```bash
    $ cd ../../
    $ ./gradlew install
    ```

 * Come back to this folder and run:

    ```bash
    $ cd experimental/behave
    $ ./prepare.sh
    ```

This script will download necessary database drivers and set up
the dependencies directory with copies of the Corda fat-JAR and
the network bootstrapping tool.

# Selective Runs

If you only want to run tests of a specific tag, you can append
the following parameter to the Gradle command:

```bash
$ ../../gradlew scenario -Ptags="@cash"
# or
$ ../../gradlew scenario -Ptags="@cash,@logging"
```