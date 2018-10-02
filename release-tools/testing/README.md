# Release Tools - Test Tracker and Generator

## Introduction

This command-line tool lets the user create and track tests in the [R3T](https://r3-cev.atlassian.net/projects/R3T) JIRA project. All generic test cases are captured as tickets of type **Platform Test Template** with a label "OS" for tests pertaining to **Corda Open Source**, "ENT" for **Corda Enterprise**, and "NS" for **Corda Network Services**. These tickets can be set to **Active** or **Inactive** status based on their relevance for a particular release.

The tool creates a set of new release tests by cloning the current set of active test templates into a set of **Platform Test** tickets. These will each get assigned to the appropriate target version. Further, the tool lets the user create sub-tasks for each of the release tests, one for each release candidate. These steps are described in more detail further down.

## List Test Cases

To list the active test cases for a product, run the following command:

```bash
$ ./test-manager list-tests <PRODUCT>
```

Where `<PRODUCT>` is either `OS`, `ENT` or `NS`. This will list the test cases that are currently applicable to Corda Open Source, Corda Enterprise and Corda Network Services, respectively.

## Show Test Status

To show the status of all test runs for a specific release or release candidate, run:

```bash
$ ./test-manager status <PRODUCT> <VERSION> <CANDIDATE>
```

Here, `<VERSION>` represents the target version, e.g., product `OS` and version `3.3` would represent Corda Open Source 3.3. `<CANDIDATE>` is optional and will narrow down the report to only show the provided candidate version, e.g., `1` for `RC01`.

## Create JIRA Version

To create a new release version in JIRA, you can run the following command:

```bash
$ ./test-manager create-version <PRODUCT> <VERSION> <CANDIDATE>
```

Note that `<CANDIDATE>` is optional. This command will create new versions in the following JIRA projects: `CORDA`, `ENT`, `ENM`, `CID` and `R3T`.

## Create Release Tests

To create the set of parent tests for a new release, you can run:

```bash
$ ./test-manager create-release-tests <PRODUCT> <VERSION>
```

This will create the test cases, but none of the test run tickets for respective release candidates. Note also that "blocks"-links between active test templates will be carried across to the created test tickets.

## Create Release Candidate Tests

To create a set of test run tickets for a new release candidate, you can run:

```bash
$ ./test-manager create-release-candidate-tests <PRODUCT> <VERSION> <CANDIDATE>
```

This will create a new sub-task under each of the test tickets for `<PRODUCT>` `<VERSION>`, for release candidate `<CANDIDATE>`.

## Options

Each command described above has a set of additional options. More specifically, if you want to use a particular JIRA user instead of being prompted for a user name every time, you can specify `--user <USER>`. For verbose logging, you can supply `--verbose` or `-v`. And to auto-reply to the prompt of whether to proceed or not, provide `--yes` or `-y`.

There is also a useful dry-run option, `--dry-run` or `-d`, that lets you run through the command without creating any tickets or applying any changes to JIRA.

## Example

As an example, say you want to create test cases for Corda Network Services 1.0 RC01. You would then follow the following steps:

```bash
$ ./test-manager create-version NS 1.0   # Create "Corda Network Services 1.0" - if it doesn't exist
$ ./test-manager create-version NS 1.0 1 # Create "Corda Network Services 1.0 RC01" - if it doesn't exist
$ ./test-manager create-release-tests NS 1.0 # Create test cases
$ ./test-manager create-release-candidate-tests NS 1.0 1 # Create test run for release candidate
```

Later, when it's time to test RC02, you simply run the following:

```bash
$ ./test-manager create-version NS 1.0 2
$ ./test-manager create-release-candidate-tests NS 1.0 2
```

That's it. Voila, you've got yourself a whole new set of JIRA tickets :-)
