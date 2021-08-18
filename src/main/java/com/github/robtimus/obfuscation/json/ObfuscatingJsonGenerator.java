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

import static com.github.robtimus.obfuscation.support.ObfuscatorUtils.writer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonGeneratorFactory;

class ObfuscatingJsonGenerator implements JsonGenerator {

    private final JsonGeneratorFactory jsonGeneratorFactory;
    private final JsonGenerator originalDelegate;
    private final JSONObfuscatorWriter writer;
    private final Map<String, PropertyConfig> properties;
    private final boolean obfuscateToString;

    private final StringBuilder captured;
    private final Writer capturedWriter;

    private JsonGenerator delegate;
    private PropertyConfig currentProperty;
    private int depth = 0;

    @SuppressWarnings("resource")
    ObfuscatingJsonGenerator(JsonGeneratorFactory jsonGeneratorFactory, JSONObfuscatorWriter writer, Map<String, PropertyConfig> properties,
            boolean obfuscateToString) {

        this.jsonGeneratorFactory = jsonGeneratorFactory;
        this.originalDelegate = jsonGeneratorFactory.createGenerator(new DontCloseWriter(writer));
        this.writer = writer;
        this.properties = properties;
        this.obfuscateToString = obfuscateToString;

        this.captured = new StringBuilder();
        this.capturedWriter = writer(captured);

        this.delegate = originalDelegate;
    }

    // Writing will flush the jsonGenerator after each write, so writes are done to the correct writer at the time.
    // The try-finally will ensure that these flushes are not propagated to the writer's backing writer.

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator writeStartObject() {
        if (currentProperty != null) {
            if (depth == 0) {
                if (currentProperty.obfuscateObjects) {
                    doWriteStart();

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
                    doWriteStart();

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
                // The end of obfuscating an object or array
                doWriteEnd();

                currentProperty = null;
            }
            // else still in a nested object that's being obfuscated
        }
        // else currently no object is being obfuscated
        return this;
    }

    private void doWriteStart() {
        if (currentProperty.isObfuscating()) {
            if (obfuscateToString) {
                // A new delegate is needed to be able to start a new object or array
                delegate = jsonGeneratorFactory.createGenerator(capturedWriter);
            } else {
                writer.startObfuscate(currentProperty.obfuscator);
            }
        }
    }

    private void doWriteEnd() {
        if (currentProperty.isObfuscating()) {
            if (obfuscateToString) {
                // The delegate is a new generator around the writer around captured; close it and write the captured content as a string
                assert delegate != originalDelegate : "delegate must be a capturing generator"; //$NON-NLS-1$
                delegate.close();
                delegate = originalDelegate;
                writeQuoted(captured);
                captured.delete(0, captured.length());
            } else {
                // Obfuscation has started on writer; end obfuscation of the current object or array
                try {
                    writer.endObfuscate();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
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
        if (shouldObfuscateCurrentProperty()) {
            writeString(value);

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
        if (shouldObfuscateCurrentProperty()) {
            writeNonString(value.toString());

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
        if (shouldObfuscateCurrentProperty()) {
            writeNonString(value.toString());

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
        if (shouldObfuscateCurrentProperty()) {
            writeNonString(Integer.toString(value));

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
        if (shouldObfuscateCurrentProperty()) {
            writeNonString(Long.toString(value));

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
        if (shouldObfuscateCurrentProperty()) {
            writeNonString(Double.toString(value));

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
        if (shouldObfuscateCurrentProperty()) {
            writeNonString(Boolean.toString(value));

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
        if (shouldObfuscateCurrentProperty()) {
            writeNonString("null"); //$NON-NLS-1$

            currentProperty = null;
        } else {
            // Not obfuscating, or in a nested object or array that's being obfuscated; just delegate
            delegate.writeNull();
        }

        return this;
    }

    private boolean shouldObfuscateCurrentProperty() {
        return currentProperty != null && currentProperty.isObfuscating() && depth == 0;
    }

    private void writeString(CharSequence value) {
        writeQuoted(value);
    }

    private void writeNonString(CharSequence value) {
        if (obfuscateToString) {
            writeQuoted(value);
        } else {
            writeUnquoted(value);
        }
    }

    @SuppressWarnings("resource")
    private void writeQuoted(CharSequence value) {
        try {
            writer.preventFlush();
            delegate.write(currentProperty.obfuscator.obfuscateText(value).toString());
            delegate.flush();
        } finally {
            writer.allowFlush();
        }
    }

    @SuppressWarnings("resource")
    private void writeUnquoted(CharSequence value) {
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
