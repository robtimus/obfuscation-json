/*
 * DontCloseReaderTest.java
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.CharBuffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@SuppressWarnings("nls")
class DontCloseReaderTest {

    private static final String SOURCE = "Lorem ipsum dolor sit amet, consectetuer adipiscing elit. Aenean commodo ligula eget dolor. Aenean massa.";

    @Test
    @DisplayName("read(CharBuffer)")
    void testReadCharBuffer() throws IOException {
        StringReader input = spy(new StringReader(SOURCE));
        StringBuilder output = new StringBuilder(SOURCE.length());

        try (Reader wrapped = new DontCloseReader(input)) {
            CharBuffer buffer = CharBuffer.allocate(10);
            while (wrapped.read(buffer) != -1) {
                buffer.flip();
                output.append(buffer);
            }
        }
        assertEquals(SOURCE, output.toString());
        verify(input, atLeastOnce()).read(any(CharBuffer.class));
        verify(input, never()).close();
    }

    @Test
    @DisplayName("read()")
    void testReadChar() throws IOException {
        StringReader input = spy(new StringReader(SOURCE));
        StringBuilder output = new StringBuilder(SOURCE.length());

        try (Reader wrapped = new DontCloseReader(input)) {
            int c;
            while ((c = wrapped.read()) != -1) {
                output.append((char) c);
            }
        }
        assertEquals(SOURCE, output.toString());
        verify(input, atLeastOnce()).read();
        verify(input, never()).close();
    }

    @Test
    @DisplayName("read(char[])")
    void testReadCharArray() throws IOException {
        StringReader input = spy(new StringReader(SOURCE));
        StringBuilder output = new StringBuilder(SOURCE.length());

        try (Reader wrapped = new DontCloseReader(input)) {
            char[] buffer = new char[10];
            int len;
            while ((len = wrapped.read(buffer)) != -1) {
                output.append(buffer, 0, len);
            }
        }
        assertEquals(SOURCE, output.toString());
        verify(input, atLeastOnce()).read(any(char[].class));
        verify(input, never()).close();
    }

    @Test
    @DisplayName("read(char[], int, int)")
    void testReadByteArrayRange() throws IOException {
        StringReader input = spy(new StringReader(SOURCE));
        StringBuilder output = new StringBuilder(SOURCE.length());

        try (Reader wrapped = new DontCloseReader(input)) {
            char[] buffer = new char[1024];
            final int offset = 100;
            int len;
            while ((len = wrapped.read(buffer, offset, 10)) != -1) {
                output.append(buffer, 100, len);
            }
        }
        assertEquals(SOURCE, output.toString());
        verify(input, atLeastOnce()).read(any(), anyInt(), anyInt());
        verify(input, never()).close();
    }

    @Test
    @DisplayName("skip(long)")
    void testSkip() throws IOException {
        StringReader input = spy(new StringReader(SOURCE));

        try (Reader wrapped = new DontCloseReader(input)) {
            assertEquals(SOURCE.length(), wrapped.skip(Integer.MAX_VALUE));
            assertEquals(-1, wrapped.read());
        }
        verify(input, times(1)).skip(anyLong());
        verify(input, never()).close();
    }

    @Test
    @DisplayName("ready()")
    void testReady() throws IOException {
        StringReader input = spy(new StringReader(SOURCE));

        try (Reader wrapped = new DontCloseReader(input)) {
            assertEquals(input.ready(), wrapped.ready());
        }
        verify(input, times(2)).ready();
        verify(input, never()).close();
    }

    @Test
    @DisplayName("mark(int) and reset")
    void testMarkAndReset() throws IOException {
        StringReader input = spy(new StringReader(SOURCE));
        StringBuilder output = new StringBuilder(SOURCE.length());

        try (Reader wrapped = new DontCloseReader(input)) {
            assertEquals(input.markSupported(), wrapped.markSupported());
            wrapped.mark(10);
            char[] buffer = new char[10];
            int len;
            wrapped.read(buffer);
            wrapped.reset();
            while ((len = wrapped.read(buffer)) != -1) {
                output.append(buffer, 0, len);
            }
        }
        assertEquals(SOURCE, output.toString());
        verify(input, times(2)).markSupported();
        verify(input, times(1)).mark(anyInt());
        verify(input, times(1)).reset();
        verify(input, never()).close();
    }
}
