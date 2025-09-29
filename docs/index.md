# Welcome to Kappa


Kappa is an OpenAPI library for the JVM. It supports [contract-first API development](https://bump.sh/blog/dev-guide-api-design-first/), and can be used for

 * [Request validation](./spring-boot/request-validation)
 * [Contract testing](./spring-boot/contract-testing)

Kappa provides first-class Spring Boot integration for both usecases. It also has adapters to work with other HTTP-related Java frameworks and libraries, like RestAssured, [Vert.x](./other-frameworks/vertx.md) and [Undertow](./other-frameworks/undertow.md).

## Version compatibility


Kappa targets providing complete support for OpenAPI 3.1. Currently it uses a [draft2020-12 compliant validator for JSON Schema](https://github.com/erosb/json-sKema).

## Relation to OpenAPI4j

Kappa is a permanent fork (successor) of the archived [OpenAPI4J](https://github.com/openapi4j/openapi4j) project. The first generally available version of Kappa is 2.0.0. Previous 1.x versions were released under the OpenAPI4j name.

