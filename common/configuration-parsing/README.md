# configuration-parsing

This module provides types and functions to facilitate using Typesafe configuration objects.

## Features

1. A multi-step, structured validation framework for Typesafe configurations, allowing to merge Typesafe and application-level rules.
2. A parsing framework, allowing to extract domain types from raw configuration objects in a versioned, type-safe fashion.
3. A configuration description framework, allowing to print the expected schema of a configuration object.
4. A configuration serialization framework, allowing to output the structure and values of a configuration object, potentially obfuscating sensitive data.

## Concepts

The main idea is to create a `Configuration.Specification` to model the expected structure of a Typesafe configuration.
The specification is then able to parse, validate, describe and serialize a raw Typesafe configuration.

By using `VersionedConfigurationParser`, it is possible to map specific versions to `Configuration.Specification`s and to parse and validate a raw configuration object based on a version header.

Refer to the following tests to gain an understanding of how the library works:

- net.corda.common.configuration.parsing.internal.versioned.VersionedParsingExampleTest
- net.corda.common.configuration.parsing.internal.SpecificationTest
- net.corda.common.configuration.parsing.internal.SchemaTest