package com.github.erosb.jsonsKema.examples;

import com.github.erosb.jsonsKema.JsonParser;
import com.github.erosb.jsonsKema.JsonValue;
import com.github.erosb.jsonsKema.Schema;
import com.github.erosb.jsonsKema.SchemaLoader;
import com.github.erosb.jsonsKema.ValidationFailure;
import com.github.erosb.jsonsKema.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class JavaTest {

    @Test
    void simple() {
        // parse the schema JSON as string
        JsonValue schemaJson = new JsonParser("""
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
    }
}
