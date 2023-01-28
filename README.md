# obfuscation-json
[![Maven Central](https://img.shields.io/maven-central/v/com.github.robtimus/obfuscation-json)](https://search.maven.org/artifact/com.github.robtimus/obfuscation-json)
[![Build Status](https://github.com/robtimus/obfuscation-json/actions/workflows/build.yml/badge.svg)](https://github.com/robtimus/obfuscation-json/actions/workflows/build.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=com.github.robtimus%3Aobfuscation-json&metric=alert_status)](https://sonarcloud.io/summary/overall?id=com.github.robtimus%3Aobfuscation-json)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=com.github.robtimus%3Aobfuscation-json&metric=coverage)](https://sonarcloud.io/summary/overall?id=com.github.robtimus%3Aobfuscation-json)
[![Known Vulnerabilities](https://snyk.io/test/github/robtimus/obfuscation-json/badge.svg)](https://snyk.io/test/github/robtimus/obfuscation-json)

Provides functionality for obfuscating JSON documents. This can be useful for logging such documents, e.g. as part of request/response logging, where sensitive properties like passwords should not be logged as-is.

To create a JSON obfuscator, simply create a builder, add properties to it, and let it build the final obfuscator:

    Obfuscator obfuscator = JSONObfuscator.builder()
            .withProperty("password", Obfuscator.fixedLength(3))
            .build();

## Disabling obfuscation for objects and/or arrays

By default, a JSON obfuscator will obfuscate all properties; for object and array properties, their contents in the document including opening and closing characters will be obfuscated. This can be turned on or off for all properties, or per property. For example:

    Obfuscator obfuscator = JSONObfuscator.builder()
            .scalarsOnlyByDefault()
            .withProperty("password", Obfuscator.fixedLength(3))
            .withProperty("complex", Obfuscator.fixedLength(3))
                    .includeObjects() // override the default setting
            .build();

## Pretty-printing

JSON obfuscators perform obfuscating by generating new, obfuscated JSON documents. By default this will use pretty-printing. This can be turned off when creating JSON obfuscators:

    Obfuscator obfuscator = JSONObfuscator.builder()
            .withProperty("password", Obfuscator.fixedLength(3))
            .withPrettyPrinting(false)
            .build();

If the structure of the original JSON document needs to be kept intact, you should use [obfuscation-jackson](https://robtimus.github.io/obfuscation-jackson/) instead.

## Producing valid JSON

If string values are obfuscated, the obfuscated value remains quoted. For other values, the obfuscated values are not quoted. This could lead to invalid JSON. For instance:

    {
      "boolean": ***
    }

For most use cases this is not an issue. If the obfuscated JSON needs to be valid, this can be achieved by converting obfuscated values to strings:

    Obfuscator obfuscator = JSONObfuscator.builder()
            .withProperty("boolean", Obfuscator.fixedLength(3))
            .produceValidJSON()
            .build();

This will turn the above result into this:

    {
        "boolean": "***"
    }

An exception is made for [Obfuscator.none()](https://robtimus.github.io/obfuscation-core/apidocs/com/github/robtimus/obfuscation/Obfuscator.html#none--). Since this obfuscator does not actually obfuscate anything, any property that is configured to use it will be added as-is. This still allows skipping obfuscating values inside certain properties:

    Obfuscator obfuscator = JSONObfuscator.builder()
            .withProperty("object", Obfuscator.none())
            .withProperty("boolean", Obfuscator.fixedLength(3))
            .produceValidJSON()
            .build();

Possible output:

    {
        "boolean": "***",
        "object": {
            "boolean": true
        }
    }

## Handling malformed JSON

If malformed JSON is encountered, obfuscation aborts. It will add a message to the result indicating that obfuscation was aborted. This message can be changed or turned off when creating JSON obfuscators:

    Obfuscator obfuscator = JSONObfuscator.builder()
            .withProperty("password", Obfuscator.fixedLength(3))
            // use null to turn it off
            .withMalformedJSONWarning("<invalid JSON>")
            .build();

## Changing JSON implementation

This library uses the Java API for Processing JSON ([JSR 374](https://www.jcp.org/en/jsr/detail?id=374)) for parsing and generating JSON. By default it uses the Glassfish reference implementation. If you want to use a different implementation instead, you should exclude the Glassfish dependency, and add a dependency for that different implementation. In your POM:

    <dependency>
      <groupId>com.github.robtimus</groupId>
      <artifactId>obfuscation-json</artifactId>
      <version>...</version>
      <exclusions>
        <exclusion>
          <groupId>org.glassfish</groupId>
          <artifactId>javax.json</artifactId>
        </exclusion>
      </exclusions>
     </dependency>
    
    <dependency>
        ...
    </dependency>
