/*
 * ObfuscatingJsonGenerator.java
 * Copyright 2020 Rob Spoor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.robtimus.obfuscation.json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.stream.JsonGenerator;

class ObfuscatingJsonGenerator implements JsonGenerator {

    private final JsonGenerator delegate;
    private final JSONObfuscatorWriter writer;
    private final Map<String, PropertyConfig> properties;

    private PropertyConfig currentProperty;
    private int depth = 0;

    ObfuscatingJsonGenerator(JsonGenerator delegate, JSONObfuscatorWriter writer, Map<String, PropertyConfig> properties) {
        this.delegate = delegate;
        this.writer = writer;
        this.properties = properties;
    }

    // Writing will flush the jsonGenerator after each write, so writes are done to the correct writer at the time.
    // The try-finally will ensure that these flushes are not propagated to the writer's backing writer.

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator writeStartObject() {
        if (currentProperty != null) {
            if (depth == 0) {
                if (currentProperty.obfuscateObjects) {
                    writer.startObfuscate(currentProperty.obfuscator);

                    depth++;
                } else {
                    // There is an obfuscator for the object property, but the obfuscation mode prohibits obfuscating objects; reset the obfuscation
                    currentProperty = null;
                }
            } else {
                // In a nested object or array that's being obfuscated; do nothing
                depth++;
            }
        }
        // else not obfuscating

        try {
            writer.preventFlush();
            delegate.writeStartObject();
            delegate.flush();
        } finally {
            writer.allowFlush();
        }
        return this;
    }

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator writeStartObject(String name) {
        // Don't delegate but split instead
        writeKey(name);
        writeStartObject();
        return this;
    }

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator writeKey(String name) {
        if (currentProperty == null) {
            currentProperty = properties.get(name);
        }
        // else in a nested object or array that's being obfuscated; do nothing

        try {
            writer.preventFlush();
            delegate.writeKey(name);
            delegate.flush();
        } finally {
            writer.allowFlush();
        }
        return this;
    }

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator writeStartArray() {
        if (currentProperty != null) {
            if (depth == 0) {
                if (currentProperty.obfuscateArrays) {
                    writer.startObfuscate(currentProperty.obfuscator);

                    depth++;
                } else {
                    // There is an obfuscator for the array property, but the obfuscation mode prohibits obfuscating arrays; reset the obfuscation
                    currentProperty = null;
                }
            } else {
                // In a nested object or array that's being obfuscated; do nothing
                depth++;
            }
        }
        // else not obfuscating

        try {
            writer.preventFlush();
            delegate.writeStartArray();
            delegate.flush();
        } finally {
            writer.allowFlush();
        }
        return this;
    }

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator writeStartArray(String name) {
        // Don't delegate but split instead
        writeKey(name);
        writeStartArray();
        return this;
    }

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator write(String name, JsonValue value) {
        // Don't delegate but split instead
        writeKey(name);
        write(value);
        return this;
    }

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator write(String name, String value) {
        // Don't delegate but split instead
        writeKey(name);
        write(value);
        return this;
    }

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator write(String name, BigInteger value) {
        // Don't delegate but split instead
        writeKey(name);
        write(value);
        return this;
    }

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator write(String name, BigDecimal value) {
        // Don't delegate but split instead
        writeKey(name);
        write(value);
        return this;
    }

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator write(String name, int value) {
        // Don't delegate but split instead
        writeKey(name);
        write(value);
        return this;
    }

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator write(String name, long value) {
        // Don't delegate but split instead
        writeKey(name);
        write(value);
        return this;
    }

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator write(String name, double value) {
        // Don't delegate but split instead
        writeKey(name);
        write(value);
        return this;
    }

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator write(String name, boolean value) {
        // Don't delegate but split instead
        writeKey(name);
        write(value);
        return this;
    }

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator writeNull(String name) {
        // Don't delegate but split instead
        writeKey(name);
        writeNull();
        return this;
    }

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator writeEnd() {
        try {
            writer.preventFlush();
            delegate.writeEnd();
            delegate.flush();
        } finally {
            writer.allowFlush();
        }

        if (currentProperty != null) {
            depth--;
            if (depth == 0) {
                // The end of obfuscating an object or array; the delegate is the obfuscator's stream, so close it to finish obfuscation
                try {
                    writer.endObfuscate();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }

                currentProperty = null;
            }
            // else still in a nested object that's being obfuscated
        }
        // else currently no object is being obfuscated
        return this;
    }

    @Override
    public JsonGenerator write(JsonValue value) {
        // Don't delegate but call correct write methods based on the type
        switch (value.getValueType()) {
        case OBJECT:
            write(value.asJsonObject());
            break;
        case ARRAY:
            write(value.asJsonArray());
            break;
        case STRING:
            write((JsonString) value);
            break;
        case NUMBER:
            write((JsonNumber) value);
            break;
        case TRUE:
            write(true);
            break;
        case FALSE:
            write(false);
            break;
        case NULL:
            writeNull();
            break;
        default:
            // Should not occur
            break;
        }

        return this;
    }

    @SuppressWarnings("resource")
    private void write(JsonObject object) {
        writeStartObject();
        for (Map.Entry<String, JsonValue> entry : object.entrySet()) {
            write(entry.getKey(), entry.getValue());
        }
        writeEnd();
    }

    @SuppressWarnings("resource")
    private void write(JsonArray array) {
        writeStartArray();
        for (JsonValue element : array) {
            write(element);
        }
        writeEnd();
    }

    @SuppressWarnings("resource")
    private void write(JsonString string) {
        write(string.getString());
    }

    @SuppressWarnings("resource")
    private void write(JsonNumber number) {
        if (number.isIntegral()) {
            try {
                write(number.longValueExact());
            } catch (@SuppressWarnings("unused") ArithmeticException e) {
                write(number.bigIntegerValue());
            }
        } else {
            write(number.bigDecimalValue());
        }
    }

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator write(String value) {
        if (currentProperty != null && depth == 0) {
            writeQuoted(value);

            currentProperty = null;
        } else {
            // Not obfuscating, or in a nested object or array that's being obfuscated; just delegate
            delegate.write(value);
        }

        return this;
    }

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator write(BigDecimal value) {
        if (currentProperty != null && depth == 0) {
            writeUnquoted(value.toString());

            currentProperty = null;
        } else {
            // Not obfuscating, or in a nested object or array that's being obfuscated; just delegate
            delegate.write(value);
        }

        return this;
    }

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator write(BigInteger value) {
        if (currentProperty != null && depth == 0) {
            writeUnquoted(value.toString());

            currentProperty = null;
        } else {
            // Not obfuscating, or in a nested object or array that's being obfuscated; just delegate
            delegate.write(value);
        }

        return this;
    }

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator write(int value) {
        if (currentProperty != null && depth == 0) {
            writeUnquoted(Integer.toString(value));

            currentProperty = null;
        } else {
            // Not obfuscating, or in a nested object or array that's being obfuscated; just delegate
            delegate.write(value);
        }

        return this;
    }

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator write(long value) {
        if (currentProperty != null && depth == 0) {
            writeUnquoted(Long.toString(value));

            currentProperty = null;
        } else {
            // Not obfuscating, or in a nested object or array that's being obfuscated; just delegate
            delegate.write(value);
        }

        return this;
    }

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator write(double value) {
        if (currentProperty != null && depth == 0) {
            writeUnquoted(Double.toString(value));

            currentProperty = null;
        } else {
            // Not obfuscating, or in a nested object or array that's being obfuscated; just delegate
            delegate.write(value);
        }

        return this;
    }

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator write(boolean value) {
        if (currentProperty != null && depth == 0) {
            writeUnquoted(Boolean.toString(value));

            currentProperty = null;
        } else {
            // Not obfuscating, or in a nested object or array that's being obfuscated; just delegate
            delegate.write(value);
        }

        return this;
    }

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator writeNull() {
        if (currentProperty != null && depth == 0) {
            writeUnquoted("null"); //$NON-NLS-1$

            currentProperty = null;
        } else {
            // Not obfuscating, or in a nested object or array that's being obfuscated; just delegate
            delegate.writeNull();
        }

        return this;
    }

    @SuppressWarnings("resource")
    private void writeQuoted(String value) {
        try {
            writer.preventFlush();
            delegate.write(currentProperty.obfuscator.obfuscateText(value).toString());
            delegate.flush();
        } finally {
            writer.allowFlush();
        }
    }

    @SuppressWarnings("resource")
    private void writeUnquoted(String value) {
        try {
            writer.preventFlush();
            writer.startUnquote();
            delegate.write(currentProperty.obfuscator.obfuscateText(value).toString());
            delegate.flush();
            writer.endUnquote();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.allowFlush();
        }
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public void flush() {
        delegate.flush();
    }
}
