# Introduction

This project illustrates how one can use Cucumber / BDD to drive
and test homogeneous and heterogeneous Corda networks on a local
machine. The framework has built-in support for Dockerised node
dependencies so that you easily can spin up a Corda node locally
that, for instance, uses a 3rd party database provider such as
MS SQL Server or Postgres.

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
    experimental/behave> cd ../../
    > ./gradlew install
    ```

 * Come back to this folder and run:

    ```bash
    > cd experimental/behave
    experimental/behave> ./prepare.sh
    ```

This script will download necessary database drivers and set up
the dependencies directory with copies of the Corda fat-JAR and
the network bootstrapping tool.

# Unit Tests

Note that the unit tests for this experimental module is not
included in the root project build, so running `./gradlew test`
from the top level directory will simply skip all tests in this
sub-project.

To run the unit tests, navigate to the `behave`-folder and run
the following command:

```bash
experimental/behave> ../../gradlew test
```

# Selective Runs

If you only want to run tests of a specific tag, you can append
the following parameter to the Gradle command:

```bash
experimental/behave> ../../gradlew scenario -Ptags="@cash"
# or
experimental/behave> ../../gradlew scenario -Ptags="@cash,@logging"
```