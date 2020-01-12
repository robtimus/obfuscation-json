# obfuscation-json

Provides functionality for obfuscating JSON documents. This can be useful for logging such documents, e.g. as part of request/response logging, where sensitive properties like passwords should not be logged as-is.

To create a JSON obfuscator, simply create a builder, add properties to it, and let it build the final obfuscator:

    Obfuscator obfuscator = JSONObfuscator.builder()
            .withProperty("password", Obfuscator.fixedLength(3))
            .build();

By default this will obfuscate all properties; for object and array properties, their contents in the document including opening and closing characters will be obfuscated. This can be turned off by specifying that only scalars should be obfuscated:

    Obfuscator obfuscator = JSONObfuscator.builder()
            .withProperty("password", Obfuscator.fixedLength(3))
            .withObfuscationMode(ObfuscationMode.SCALAR)
            .build();

## Pretty-printing

JSON obfuscators perform obfuscating by generating new, obfuscated JSON documents. By default this will use pretty-printing. This can be turned off when creating JSON obfuscators:

    Obfuscator obfuscator = JSONObfuscator.builder()
            .withProperty("password", Obfuscator.fixedLength(3))
            .withPrettyPrinting(false)
            .build();

If the structure of the original JSON document needs to be kept intact, you should use [obfuscation-jackson](https://robtimus.github.io/obfuscation-jackson/) instead.

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
