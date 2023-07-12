# json-sKema

_json-Skema is a [Json Schema](https://json-schema.org/) validator library for the Java Virtual Machine. It implements the [draft 2020-12](https://json-schema.org/draft/2020-12/json-schema-validation.html) specification._

Are you new to JSON Schema? Get started with [Understanding JSON Schema](https://json-schema.org/understanding-json-schema/)!

## Installation

### Maven

Add the following dependency to the `<dependencies>` section of your project:

```xml
<dependency>
    <groupId>com.github.erosb</groupId>
    <artifactId>json-sKema</artifactId>
    <version>0.8.0</version>
</dependency>
```

### Gradle

```groovy
dependencies {
    implementation("com.github.erosb:json-sKema:0.8.0")
}
```

## Usage

### Hello-world

```java
// parse the schema JSON as string
JsonValue  schemaJson = new JsonParser("""
        {
            "type": "object",
            "properties": {
                "age": {
                    "type": "number",
                    "minimum": 0
                },
                "name": {
                    "type": "string"
                }
            }
        }
        """).parse();
// map the raw json to a reusable Schema instance
Schema schema = new SchemaLoader(schemaJson).load();

// create a validator instance for each validation (one-time use object) 
Validator validator = Validator.forSchema(schema);

// parse the input instance to validate against the schema
JsonValue instance = new JsonParser("""
        {
            "age": -5,
            "name": null
        }
        """).parse();

// run the validation
ValidationFailure failure = validator.validate(instance);

// print the validation failures (if any)
System.out.println(failure);
```

### Loading a schema file from URL

```java
// HTTP protocol is also supported
Schema schema = SchemaLoader.forURL("classpath:///path/to/your/schema.json").load();

// create a validator instance for each validation (one-time use object) 
Validator validator = Validator.forSchema(schema);
// ...
```

## Compatibility notes

The library implements the JSON Schema draft 2020-12 core and validation specifications, with the following notes:
 * `$dynamicAnchor` and `$dynamicRef` support is partially implemented

### `"format"` support

The library currently has built-in support for the following `"format"` values defined in the specification:

<table>
    <thead>
        <tr>
            <td>"format"</td>
            <td>Supported?</td>
            <td>Notes</td>
        </tr>
    </thead>
<tbody>
    <tr>
        <td>date</td>
        <td>Yes</td>
        <td></td>
    </tr>
    <tr>
        <td>date-time</td>
        <td>Yes</td>
        <td>Non-UTC values with leap seconds not supported</td>
    </tr>
    <tr>
        <td>time</td>
        <td>Yes</td>
        <td>Leap seconds not supported</td>
    </tr>
    <tr>
        <td>duration</td>
        <td>Partially</td>
        <td></td>
    </tr>
    <tr>
        <td>email</td>
        <td>Yes</td>
        <td>IPV6 domain parts not supported</td>
    </tr>
    <tr>
        <td>uri</td>
        <td>Yes</td>
        <td></td>
    </tr>
    <tr>
        <td>ipv4</td>
        <td>Yes</td>
        <td></td>
    </tr>
    <tr>
        <td>ipv6</td>
        <td>Yes</td>
        <td></td>
    </tr>
    <tr>
        <td>uuid</td>
        <td>Yes</td>
        <td></td>
    </tr>
</tbody>
</table>

The following formats are NOT supported: hostname, idn-email, idn-hostname, iri, iri-reference, json-pointer, regex, relative-json-pointer, uri-reference, uri-template .


### Support for older JSON Schema drafts

This project is the successor of [everit-org/json-schema](https://github.com/everit-org/json-schema). If you want to use draft-04, draft-06 or draft-07 versions of JSON Schema, then you can use the everit library.


## Contribution guideline

Local environment setup:

_Prerequisite: JDK and Maven installed_

```
git clone https://github.com/erosb/json-sKema.git
cd json-sKema
git submodule init
git submodule update
```

### Building the project:

`mvn clean package`

### Building the project and running the official test suite:

Test annotated with `@Tag("acceptance")` require the test suite to be pulled using:

`git submodule update --init --recursive`

Then run the tests:

`mvn clean verify`

### Building the project without running the official test suite:
`mvn clean package -Dgroups='!acceptance'`
