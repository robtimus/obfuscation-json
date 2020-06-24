/*
 * DontCloseWriterTest.java
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@SuppressWarnings("nls")
class DontCloseWriterTest {

    private static final String SOURCE = "Lorem ipsum dolor sit amet, consectetuer adipiscing elit. Aenean commodo ligula eget dolor. Aenean massa.";

    @Test
    @DisplayName("write(int)")
    void testWriteInt() throws IOException {
        StringWriter output = spy(new StringWriter(SOURCE.length()));

        try (Writer wrapped = new DontCloseWriter(output)) {
            for (int i = 0; i < SOURCE.length(); i++) {
                wrapped.write(SOURCE.charAt(i));
            }
        }
        assertEquals(SOURCE, output.toString());
        verify(output, atLeastOnce()).write(anyInt());
        verify(output, never()).close();
    }

    @Test
    @DisplayName("write(char[])")
    void testWriteCharArray() throws IOException {
        StringWriter output = spy(new StringWriter(SOURCE.length()));

        try (Writer wrapped = new DontCloseWriter(output)) {
            wrapped.write(SOURCE.toCharArray());
        }
        assertEquals(SOURCE, output.toString());
        verify(output, atLeastOnce()).write(any(char[].class));
        verify(output, never()).close();
    }

    @Test
    @DisplayName("write(char[], int, int)")
    void testWriteCharArrayRange() throws IOException {
        StringWriter output = spy(new StringWriter(SOURCE.length()));

        try (Writer wrapped = new DontCloseWriter(output)) {
            char[] chars = SOURCE.toCharArray();
            int index = 0;
            while (index < chars.length) {
                int to = Math.min(index + 5, SOURCE.length());
                wrapped.write(chars, index, to - index);
                index = to;
            }
        }
        assertEquals(SOURCE, output.toString());
        verify(output, atLeastOnce()).write(any(char[].class), anyInt(), anyInt());
        verify(output, never()).close();
    }

    @Test
    @DisplayName("write(String)")
    void testWriteString() throws IOException {
        StringWriter output = spy(new StringWriter(SOURCE.length()));

        try (Writer wrapped = new DontCloseWriter(output)) {
            wrapped.write(SOURCE);
        }
        assertEquals(SOURCE, output.toString());
        verify(output, atLeastOnce()).write(any(String.class));
        verify(output, never()).close();
    }

    @Test
    @DisplayName("write(String, int, int)")
    void testWriteStringRange() throws IOException {
        StringWriter output = spy(new StringWriter(SOURCE.length()));

        try (Writer wrapped = new DontCloseWriter(output)) {
            int index = 0;
            while (index < SOURCE.length()) {
                int to = Math.min(index + 5, SOURCE.length());
                wrapped.write(SOURCE, index, to - index);
                index = to;
            }
        }
        assertEquals(SOURCE, output.toString());
        verify(output, atLeastOnce()).write(any(String.class), anyInt(), anyInt());
        verify(output, never()).close();
    }

    @Test
    @DisplayName("append(CharSequence)")
    void testAppendCharSequence() throws IOException {
        StringWriter output = spy(new StringWriter(SOURCE.length()));

        try (Writer wrapped = new DontCloseWriter(output)) {
            wrapped.append(SOURCE);
        }
        assertEquals(SOURCE, output.toString());
        verify(output, atLeastOnce()).append(any());
        verify(output, never()).close();
    }

    @Test
    @DisplayName("append(CharSequence, int, int)")
    void testAppendCharSequenceRange() throws IOException {
        StringWriter output = spy(new StringWriter(SOURCE.length()));

        try (Writer wrapped = new DontCloseWriter(output)) {
            int index = 0;
            while (index < SOURCE.length()) {
                int to = Math.min(index + 5, SOURCE.length());
                wrapped.append(SOURCE, index, to);
                index = to;
            }
        }
        assertEquals(SOURCE, output.toString());
        verify(output, atLeastOnce()).append(any(), anyInt(), anyInt());
        verify(output, never()).close();
    }

    @Test
    @DisplayName("append(char)")
    void testAppendChar() throws IOException {
        StringWriter output = spy(new StringWriter(SOURCE.length()));

        try (Writer wrapped = new DontCloseWriter(output)) {
            for (int i = 0; i < SOURCE.length(); i++) {
                wrapped.append(SOURCE.charAt(i));
            }
        }
        assertEquals(SOURCE, output.toString());
        verify(output, atLeastOnce()).append(anyChar());
        verify(output, never()).close();
    }

    @Test
    @DisplayName("flush()")
    void testFlush() throws IOException {
        StringWriter output = spy(new StringWriter(SOURCE.length()));

        try (Writer wrapped = new DontCloseWriter(output)) {
            wrapped.flush();
        }
        verify(output, times(1)).flush();
        verify(output, never()).close();
    }
}
