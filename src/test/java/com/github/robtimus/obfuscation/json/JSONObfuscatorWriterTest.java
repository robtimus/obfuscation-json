/*
 * JSONObfuscatorWriterTest.java
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

import static java.util.Arrays.fill;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.github.robtimus.obfuscation.Obfuscator;

@SuppressWarnings({ "javadoc", "nls" })
public class JSONObfuscatorWriterTest {

    @Nested
    @DisplayName("endObfuscating()")
    public class EndObfuscating {

        @Test
        @DisplayName("non-obfuscating")
        public void testNonObfuscating() throws IOException {
            try (Writer delegate = new StringWriter();
                    JSONObfuscatorWriter writer = new JSONObfuscatorWriter(delegate)) {

                writer.endObfuscating();
                writer.write("hello world");

                assertEquals("hello world", delegate.toString());
            }
        }

        @Test
        @DisplayName("obfuscating")
        public void testObfuscating() throws IOException {
            try (Writer delegate = new StringWriter();
                    JSONObfuscatorWriter writer = new JSONObfuscatorWriter(delegate)) {

                writer.startObfuscate(Obfuscator.all());
                writer.endObfuscating();
                writer.write("hello world");

                assertEquals("hello world", delegate.toString());
            }
        }
    }

    @Nested
    @DisplayName("assertNonObfuscating()")
    public class AssertNonObfuscating {

        @Test
        @DisplayName("non-obfuscating")
        public void testNonObfuscating() throws IOException {
            try (JSONObfuscatorWriter writer = new JSONObfuscatorWriter(new StringWriter())) {
                writer.assertNonObfuscating();
            }
        }

        @Test
        @DisplayName("obfuscating")
        public void testObfuscating() throws IOException {
            try (JSONObfuscatorWriter writer = new JSONObfuscatorWriter(new StringWriter())) {
                writer.startObfuscate(Obfuscator.all());
                assertThrows(AssertionError.class, () -> writer.assertNonObfuscating());
            }
        }
    }

    @Nested
    @DisplayName("startObfuscate()")
    public class Obfuscate {

        @Test
        @DisplayName("non-obfuscating")
        public void testNonObfuscating() throws IOException {
            try (StringWriter delegate = new StringWriter();
                    JSONObfuscatorWriter writer = new JSONObfuscatorWriter(delegate)) {

                writer.startObfuscate(Obfuscator.all());
                writer.write("hello world");
                assertEquals("***********", delegate.toString());
            }
        }

        @Test
        @DisplayName("obfuscating")
        public void testObfuscating() throws IOException {
            try (StringWriter delegate = new StringWriter();
                    JSONObfuscatorWriter writer = new JSONObfuscatorWriter(delegate)) {

                writer.startObfuscate(Obfuscator.all());
                assertThrows(AssertionError.class, () -> writer.startObfuscate(Obfuscator.all()));
                writer.write("hello world");
                assertEquals("***********", delegate.toString());
            }
        }
    }

    @Nested
    @DisplayName("endObfuscate()")
    public class EndObfuscate {

        @Test
        @DisplayName("non-obfuscating")
        public void testNonObfuscating() throws IOException {
            try (StringWriter delegate = new StringWriter();
                    JSONObfuscatorWriter writer = new JSONObfuscatorWriter(delegate)) {

                assertThrows(AssertionError.class, () -> writer.endObfuscate());
                writer.write("hello world");
                assertEquals("hello world", delegate.toString());
            }
        }

        @Test
        @DisplayName("obfuscating")
        public void testObfuscating() throws IOException {
            try (StringWriter delegate = new StringWriter();
                    JSONObfuscatorWriter writer = new JSONObfuscatorWriter(delegate)) {

                writer.startObfuscate(Obfuscator.all());
                writer.endObfuscate();
                writer.write("hello world");
                assertEquals("hello world", delegate.toString());
            }
        }
    }

    @Nested
    @DisplayName("startUnquote()")
    public class StartUnquote {

        @Test
        @DisplayName("non-obfuscating")
        public void testNonObfuscating() throws IOException {
            try (StringWriter delegate = new StringWriter();
                    JSONObfuscatorWriter writer = new JSONObfuscatorWriter(delegate)) {

                writer.startUnquote();
                writer.write("hello world");
                assertEquals("", delegate.toString());
            }
        }

        @Test
        @DisplayName("obfuscating")
        public void testObfuscating() throws IOException {
            try (StringWriter delegate = new StringWriter();
                    JSONObfuscatorWriter writer = new JSONObfuscatorWriter(delegate)) {

                writer.startObfuscate(Obfuscator.all());
                assertThrows(AssertionError.class, () -> writer.startUnquote());
                writer.write("hello world");
                assertEquals("***********", delegate.toString());
            }
        }
    }

    @Nested
    @DisplayName("endUnquote()")
    public class EndUnquote {

        @Test
        @DisplayName("non-quoting")
        public void testNonQuoting() throws IOException {
            try (StringWriter delegate = new StringWriter();
                    JSONObfuscatorWriter writer = new JSONObfuscatorWriter(delegate)) {

                assertThrows(AssertionError.class, () -> writer.endUnquote());
                writer.write("hello world");
                assertEquals("hello world", delegate.toString());
            }
        }

        @Test
        @DisplayName("quoting but non-quoted")
        public void testQuotingNonQuoted() throws IOException {
            try (StringWriter delegate = new StringWriter();
                    JSONObfuscatorWriter writer = new JSONObfuscatorWriter(delegate)) {

                writer.startUnquote();
                writer.write("hello world\"");
                assertThrows(AssertionError.class, () -> writer.endUnquote());
                assertEquals("", delegate.toString());
            }
            try (StringWriter delegate = new StringWriter();
                    JSONObfuscatorWriter writer = new JSONObfuscatorWriter(delegate)) {

                writer.startUnquote();
                writer.write("\"hello world");
                assertThrows(AssertionError.class, () -> writer.endUnquote());
                assertEquals("", delegate.toString());
            }
            try (StringWriter delegate = new StringWriter();
                    JSONObfuscatorWriter writer = new JSONObfuscatorWriter(delegate)) {

                writer.startUnquote();
                writer.write("\"");
                assertThrows(AssertionError.class, () -> writer.endUnquote());
                assertEquals("", delegate.toString());
            }
        }

        @Test
        @DisplayName("quoting")
        public void testQuoting() throws IOException {
            try (StringWriter delegate = new StringWriter();
                    JSONObfuscatorWriter writer = new JSONObfuscatorWriter(delegate)) {

                writer.startUnquote();
                writer.write("\"hello world\"");
                writer.endUnquote();
                assertEquals("hello world", delegate.toString());

                writer.startUnquote();
                writer.write("\"\"");
                writer.endUnquote();
                assertEquals("hello world", delegate.toString());
            }
        }
    }

    @Test
    @DisplayName("write(int)")
    @SuppressWarnings("resource")
    public void testWriteInt() throws IOException {
        try (Writer delegate = spy(new StringWriter());
                Writer writer = new JSONObfuscatorWriter(delegate)) {

            writer.write(' ');
            writer.write('x');
            writer.write(' ');
            verify(delegate).write('x');
            verify(delegate).write(' ');
            verifyNoMoreInteractions(delegate);

            assertEquals("x ", delegate.toString());
        }
    }

    @Test
    @DisplayName("write(char[])")
    @SuppressWarnings("resource")
    public void testWriteCharArray() throws IOException {
        try (Writer delegate = mock(Writer.class);
                Writer writer = new JSONObfuscatorWriter(delegate)) {

            char[] buffer = new char[5];
            writer.write(buffer);
            verify(delegate).write(buffer, 0, 5);
            verifyNoMoreInteractions(delegate);
        }
    }

    @Test
    @DisplayName("write(char[], int, int)")
    @SuppressWarnings("resource")
    public void testWriteCharArrayPortion() throws IOException {
        try (Writer delegate = spy(new StringWriter());
                Writer writer = new JSONObfuscatorWriter(delegate)) {

            char[] buffer = new char[0];
            writer.write(buffer, 0, 0);

            buffer = new char[5];
            fill(buffer, '\t');
            writer.write(buffer, 0, 3);
            writer.write(buffer, 0, 5);

            buffer = "\t\thello world".toCharArray();
            writer.write(buffer, 0, buffer.length);
            verify(delegate).write(buffer, 2, buffer.length - 2);

            buffer = new char[5];
            fill(buffer, '\t');
            writer.write(buffer, 0, 5);
            verify(delegate).write(buffer, 0, 5);

            verifyNoMoreInteractions(delegate);

            assertEquals("hello world\t\t\t\t\t", delegate.toString());
        }
    }

    @Test
    @DisplayName("write(String)")
    @SuppressWarnings("resource")
    public void testWriteString() throws IOException {
        try (Writer delegate = mock(Writer.class);
                Writer writer = new JSONObfuscatorWriter(delegate)) {

            writer.write("hello world");
            verify(delegate).write("hello world", 0, 11);
            verifyNoMoreInteractions(delegate);
        }
    }

    @Test
    @DisplayName("write(String, int, int)")
    @SuppressWarnings("resource")
    public void testWriteStringPortion() throws IOException {
        try (Writer delegate = spy(new StringWriter());
                Writer writer = new JSONObfuscatorWriter(delegate)) {

            writer.write("", 0, 0);

            writer.write("\t\t\t\t\t", 0, 3);
            writer.write("\t\t\t\t\t", 0, 5);

            writer.write("\t\thello world", 0, 13);
            verify(delegate).write("\t\thello world", 2, 11);

            writer.write("\t\t\t\t\t", 0, 5);
            verify(delegate).write("\t\t\t\t\t", 0, 5);

            verifyNoMoreInteractions(delegate);

            assertEquals("hello world\t\t\t\t\t", delegate.toString());
        }
    }

    @Test
    @DisplayName("append(CharSequence)")
    @SuppressWarnings("resource")
    public void testAppendCharSequence() throws IOException {
        try (Writer delegate = mock(Writer.class);
                Writer writer = new JSONObfuscatorWriter(delegate)) {

            assertSame(writer, writer.append("hello world"));
            verify(delegate).append("hello world", 0, 11);
            verifyNoMoreInteractions(delegate);
        }
    }

    @Test
    @DisplayName("append(CharSequence, int, int)")
    public void testAppendCharSequencePortion() throws IOException {
        try (Writer delegate = new StringWriter();
                Writer writer = new JSONObfuscatorWriter(delegate)) {

            assertSame(writer, writer.append("", 0, 0));

            assertSame(writer, writer.append("\t\t\t\t\t", 0, 3));
            assertSame(writer, writer.append("\t\t\t\t\t", 0, 5));

            assertSame(writer, writer.append("\t\thello world", 0, 13));

            assertSame(writer, writer.append("\t\t\t\t\t", 0, 5));

            assertEquals("hello world\t\t\t\t\t", delegate.toString());
        }
    }

    @Test
    @DisplayName("append(char)")
    public void testAppendChar() throws IOException {
        try (Writer delegate = new StringWriter();
                Writer writer = new JSONObfuscatorWriter(delegate)) {

            assertSame(writer, writer.append(' '));
            assertSame(writer, writer.append('x'));
            assertSame(writer, writer.append(' '));

            assertEquals("x ", delegate.toString());
        }
    }

    @Test
    @DisplayName("flush()")
    public void flush() throws IOException {
        try (Writer delegate = mock(Writer.class);
                Writer writer = new JSONObfuscatorWriter(delegate)) {

            writer.flush();
            verifyNoMoreInteractions(delegate);
        }
    }

    @Test
    @DisplayName("close()")
    public void testClose() throws IOException {
        try (Writer delegate = mock(Writer.class);
                Writer writer = new JSONObfuscatorWriter(delegate)) {

            writer.close();
            verify(delegate).close();
            verifyNoMoreInteractions(delegate);
        }
    }
}
