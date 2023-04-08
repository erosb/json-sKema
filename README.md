# json-sKema

_json-Skema is a [Json Schema](https://json-schema.org/) validator library for the Java Virtual Machine. It implements the [draft 2020-12](https://json-schema.org/draft/2020-12/json-schema-validation.html) specification._


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
