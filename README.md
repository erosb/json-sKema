# json-sKema

_json-Skema is a [Json Schema](https://json-schema.org/) validator library for the Java Virtual Machine. It implements the [draft 2020-12](https://json-schema.org/draft/2020-12/json-schema-validation.html) specification._

## Installation

### Maven

Add the following dependency to the `<dependencies>` section of your project:

```xml
<dependency>
    <groupId>com.github.erosb</groupId>
    <artifactId>json-sKema</artifactId>
    <version>0.5.0</version>
</dependency>
```

### Gradle

```groovy
dependencies {
    implementation("com.github.erosb:json-sKema:0.5.0")
}
```

## Usage

```kotlin

```

## Compatibility notes

The library implements the JSON Schema draft 2020-12 core and validation specifications, with the following notes:
 * `$dynamicAnchor` and `$dynamicRef` support is partially implemented


## Support for older JSON Schema drafts

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

Building the project:

`mvn clean package`

Building the project without running the official test suite:
`mvn clean package -Dgroups='!acceptance'`
