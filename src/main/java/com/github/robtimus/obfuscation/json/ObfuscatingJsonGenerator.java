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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonGeneratorFactory;
import com.github.robtimus.obfuscation.json.JSONObfuscator.PropertyConfigurer.ObfuscationMode;
import com.github.robtimus.obfuscation.support.LimitAppendable;

class ObfuscatingJsonGenerator implements JsonGenerator {

    private final JsonGeneratorFactory jsonGeneratorFactory;
    private final JsonGenerator originalDelegate;
    private final JSONObfuscatorWriter writer;
    private final LimitAppendable appendable;
    private final Map<String, PropertyConfig> properties;
    private final boolean produceValidJSON;

    private final StringBuilder captured;
    private final Writer capturedWriter;

    private JsonGenerator delegate;

    private final Deque<ObfuscatedProperty> currentProperties = new ArrayDeque<>();

    @SuppressWarnings("resource")
    ObfuscatingJsonGenerator(JsonGeneratorFactory jsonGeneratorFactory, JSONObfuscatorWriter writer, LimitAppendable appendable,
            Map<String, PropertyConfig> properties, boolean produceValidJSON) {

        this.jsonGeneratorFactory = jsonGeneratorFactory;
        this.originalDelegate = jsonGeneratorFactory.createGenerator(new DontCloseWriter(writer));
        this.writer = writer;
        this.appendable = appendable;
        this.properties = properties;
        this.produceValidJSON = produceValidJSON;

        this.captured = new StringBuilder();
        this.capturedWriter = writer(captured);

        this.delegate = originalDelegate;
    }

    // Writing will flush the jsonGenerator after each write, so writes are done to the correct writer at the time.
    // The try-finally will ensure that these flushes are not propagated to the writer's backing writer.

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator writeStartObject() {
        ObfuscatedProperty currentProperty = currentProperties.peekLast();
        if (currentProperty != null) {
            if (currentProperty.depth == 0) {
                // The start of the object that's being obfuscated
                ObfuscationMode obfuscationMode = currentProperty.config.forObjects;
                if (obfuscationMode == ObfuscationMode.OBFUSCATE) {
                    currentProperty.obfuscationMode = obfuscationMode;
                    currentProperty.depth++;

                    startObfuscating(currentProperty);
                } else if (obfuscationMode == ObfuscationMode.EXCLUDE) {
                    // There is an obfuscator for the object property, but the obfuscation mode prohibits obfuscating objects, so discard the property
                    currentProperties.removeLast();
                } else {
                    currentProperty.obfuscationMode = obfuscationMode;
                    currentProperty.depth++;
                }
            } else {
                // In a nested object or array that's being obfuscated; do nothing
                currentProperty.depth++;
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
        ObfuscatedProperty currentProperty = currentProperties.peekLast();
        if ((currentProperty == null || currentProperty.allowsOverriding()) && !appendable.limitExceeded()) {
            PropertyConfig config = properties.get(name);
            if (config != null) {
                currentProperty = new ObfuscatedProperty(config);
                currentProperties.addLast(currentProperty);
            }
        }
        // else in a nested object or array that's being obfuscated, or the destination limit has already been exceed; do nothing
        // The limitExceed check is added to prevent any complex logic being executed for content that will not be appended anyway

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
        ObfuscatedProperty currentProperty = currentProperties.peekLast();
        if (currentProperty != null) {
            if (currentProperty.depth == 0) {
                // The start of the array that's being obfuscated
                ObfuscationMode obfuscationMode = currentProperty.config.forArrays;
                if (obfuscationMode == ObfuscationMode.OBFUSCATE) {
                    currentProperty.obfuscationMode = obfuscationMode;
                    currentProperty.depth++;

                    startObfuscating(currentProperty);
                } else if (obfuscationMode == ObfuscationMode.EXCLUDE) {
                    // There is an obfuscator for the array property, but the obfuscation mode prohibits obfuscating arrays, so discard the property
                    currentProperties.removeLast();
                } else {
                    currentProperty.obfuscationMode = obfuscationMode;
                    currentProperty.depth++;
                }
            } else {
                // In a nested object or array that's being obfuscated; do nothing
                currentProperty.depth++;
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

        ObfuscatedProperty currentProperty = currentProperties.peekLast();
        if (currentProperty != null) {
            currentProperty.depth--;
            if (currentProperty.depth == 0) {
                // The end of obfuscating an object or array
                endObfuscating(currentProperty);

                currentProperties.removeLast();
            }
            // else still in a nested object that's being obfuscated
        }
        // else currently no object is being obfuscated
        return this;
    }

    private void startObfuscating(ObfuscatedProperty currentProperty) {
        if (currentProperty.obfuscateStructure()) {
            if (produceValidJSON) {
                // A new delegate is needed to be able to start a new object or array
                delegate = jsonGeneratorFactory.createGenerator(capturedWriter);
            } else {
                writer.startObfuscate(currentProperty.config.obfuscator);
            }
        }
    }

    private void endObfuscating(ObfuscatedProperty currentProperty) {
        if (currentProperty.obfuscateStructure()) {
            if (produceValidJSON) {
                // The delegate is a new generator around the writer around captured; close it and write the captured content as a string
                assert delegate != originalDelegate : "delegate must be a capturing generator"; //$NON-NLS-1$
                delegate.close();
                delegate = originalDelegate;
                writeQuoted(currentProperty, captured);
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
                return write(value.asJsonObject());
            case ARRAY:
                return write(value.asJsonArray());
            case STRING:
                return write((JsonString) value);
            case NUMBER:
                return write((JsonNumber) value);
            case TRUE:
                return write(true);
            case FALSE:
                return write(false);
            case NULL:
                return writeNull();
            default:
                // Should not occur
                return this;
        }
    }

    @SuppressWarnings("resource")
    private JsonGenerator write(JsonObject object) {
        writeStartObject();
        for (Map.Entry<String, JsonValue> entry : object.entrySet()) {
            write(entry.getKey(), entry.getValue());
        }
        return writeEnd();
    }

    @SuppressWarnings("resource")
    private JsonGenerator write(JsonArray array) {
        writeStartArray();
        for (JsonValue element : array) {
            write(element);
        }
        return writeEnd();
    }

    private JsonGenerator write(JsonString string) {
        return write(string.getString());
    }

    private JsonGenerator write(JsonNumber number) {
        if (number.isIntegral()) {
            try {
                return write(number.longValueExact());
            } catch (@SuppressWarnings("unused") ArithmeticException e) {
                return write(number.bigIntegerValue());
            }
        }
        return write(number.bigDecimalValue());
    }

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator write(String value) {
        ObfuscatedProperty currentProperty = currentProperties.peekLast();
        if (currentProperty != null && currentProperty.obfuscateScalar()) {
            writeString(currentProperty, value);

            if (currentProperty.depth == 0) {
                currentProperties.removeLast();
            }
        } else {
            // Not obfuscating, or in a nested object or array that's being obfuscated; just delegate
            delegate.write(value);
        }

        return this;
    }

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator write(BigDecimal value) {
        ObfuscatedProperty currentProperty = currentProperties.peekLast();
        if (currentProperty != null && currentProperty.obfuscateScalar()) {
            writeNonString(currentProperty, value.toString());

            if (currentProperty.depth == 0) {
                currentProperties.removeLast();
            }
        } else {
            // Not obfuscating, or in a nested object or array that's being obfuscated; just delegate
            delegate.write(value);
        }

        return this;
    }

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator write(BigInteger value) {
        ObfuscatedProperty currentProperty = currentProperties.peekLast();
        if (currentProperty != null && currentProperty.obfuscateScalar()) {
            writeNonString(currentProperty, value.toString());

            if (currentProperty.depth == 0) {
                currentProperties.removeLast();
            }
        } else {
            // Not obfuscating, or in a nested object or array that's being obfuscated; just delegate
            delegate.write(value);
        }

        return this;
    }

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator write(int value) {
        ObfuscatedProperty currentProperty = currentProperties.peekLast();
        if (currentProperty != null && currentProperty.obfuscateScalar()) {
            writeNonString(currentProperty, Integer.toString(value));

            if (currentProperty.depth == 0) {
                currentProperties.removeLast();
            }
        } else {
            // Not obfuscating, or in a nested object or array that's being obfuscated; just delegate
            delegate.write(value);
        }

        return this;
    }

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator write(long value) {
        ObfuscatedProperty currentProperty = currentProperties.peekLast();
        if (currentProperty != null && currentProperty.obfuscateScalar()) {
            writeNonString(currentProperty, Long.toString(value));

            if (currentProperty.depth == 0) {
                currentProperties.removeLast();
            }
        } else {
            // Not obfuscating, or in a nested object or array that's being obfuscated; just delegate
            delegate.write(value);
        }

        return this;
    }

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator write(double value) {
        ObfuscatedProperty currentProperty = currentProperties.peekLast();
        if (currentProperty != null && currentProperty.obfuscateScalar()) {
            writeNonString(currentProperty, Double.toString(value));

            if (currentProperty.depth == 0) {
                currentProperties.removeLast();
            }
        } else {
            // Not obfuscating, or in a nested object or array that's being obfuscated; just delegate
            delegate.write(value);
        }

        return this;
    }

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator write(boolean value) {
        ObfuscatedProperty currentProperty = currentProperties.peekLast();
        if (currentProperty != null && currentProperty.obfuscateScalar()) {
            writeNonString(currentProperty, Boolean.toString(value));

            if (currentProperty.depth == 0) {
                currentProperties.removeLast();
            }
        } else {
            // Not obfuscating, or in a nested object or array that's being obfuscated; just delegate
            delegate.write(value);
        }

        return this;
    }

    @Override
    @SuppressWarnings("resource")
    public JsonGenerator writeNull() {
        ObfuscatedProperty currentProperty = currentProperties.peekLast();
        if (currentProperty != null && currentProperty.obfuscateScalar()) {
            writeNonString(currentProperty, "null"); //$NON-NLS-1$

            if (currentProperty.depth == 0) {
                currentProperties.removeLast();
            }
        } else {
            // Not obfuscating, or in a nested object or array that's being obfuscated; just delegate
            delegate.writeNull();
        }

        return this;
    }

    private void writeString(ObfuscatedProperty currentProperty, CharSequence value) {
        writeQuoted(currentProperty, value);
    }

    private void writeNonString(ObfuscatedProperty currentProperty, CharSequence value) {
        if (produceValidJSON) {
            writeQuoted(currentProperty, value);
        } else {
            writeUnquoted(currentProperty, value);
        }
    }

    @SuppressWarnings("resource")
    private void writeQuoted(ObfuscatedProperty currentProperty, CharSequence value) {
        try {
            writer.preventFlush();
            delegate.write(currentProperty.config.obfuscator.obfuscateText(value).toString());
            delegate.flush();
        } finally {
            writer.allowFlush();
        }
    }

    @SuppressWarnings("resource")
    private void writeUnquoted(ObfuscatedProperty currentProperty, CharSequence value) {
        try {
            writer.preventFlush();
            writer.startUnquote();
            delegate.write(currentProperty.config.obfuscator.obfuscateText(value).toString());
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

    private static final class ObfuscatedProperty {

        private final PropertyConfig config;
        private ObfuscationMode obfuscationMode;
        private int depth = 0;

        private ObfuscatedProperty(PropertyConfig config) {
            this.config = config;
        }

        private boolean allowsOverriding() {
            // OBFUSCATE and INHERITED do not allow overriding
            // No need to include EXCLUDE; if that occurs the ObfuscatedProperty is discarded
            return obfuscationMode == ObfuscationMode.INHERIT_OVERRIDABLE;
        }

        private boolean obfuscateStructure() {
            // Don't obfuscate the entire structure if Obfuscator.none() is used
            return config.performObfuscation && obfuscationMode == ObfuscationMode.OBFUSCATE;
        }

        private boolean obfuscateScalar() {
            // Don't obfuscate the scalar if Obfuscator.none() is used
            // Obfuscate if depth == 0 (the property is for the scalar itself),
            // or if the obfuscation mode is INHERITED or INHERITED_OVERRIDABLE (EXCLUDE is discarded)
            return config.performObfuscation
                    && (depth == 0 || obfuscationMode != ObfuscationMode.OBFUSCATE);
        }
    }
}
