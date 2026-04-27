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
import jakarta.json.JsonNumber;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonGeneratorFactory;
import com.github.robtimus.obfuscation.json.JSONObfuscator.PropertyConfigurer.ObfuscationMode;
import com.github.robtimus.obfuscation.support.LimitAppendable;

class ObfuscatingJsonGenerator implements AutoCloseable {

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

    @SuppressWarnings("resource")
    void writeStartObject() {
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
    }

    @SuppressWarnings("resource")
    void writeKey(String name) {
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
    }

    @SuppressWarnings("resource")
    void writeStartArray() {
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
    }

    @SuppressWarnings("resource")
    void writeEnd() {
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

    void write(JsonNumber number) {
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

    @SuppressWarnings("resource")
    void write(String value) {
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
    }

    @SuppressWarnings("resource")
    private void write(BigDecimal value) {
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
    }

    @SuppressWarnings("resource")
    private void write(BigInteger value) {
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
    }

    @SuppressWarnings("resource")
    private void write(long value) {
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
    }

    @SuppressWarnings("resource")
    void write(boolean value) {
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
    }

    @SuppressWarnings("resource")
    void writeNull() {
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
