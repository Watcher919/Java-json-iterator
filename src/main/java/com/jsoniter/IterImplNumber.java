/*
this implementations contains significant code from https://github.com/ngs-doo/dsl-json/blob/master/LICENSE

Copyright (c) 2015, Nova Generacija Softvera d.o.o.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice,
      this list of conditions and the following disclaimer.

    * Redistributions in binary form must reproduce the above copyright notice,
      this list of conditions and the following disclaimer in the documentation
      and/or other materials provided with the distribution.

    * Neither the name of Nova Generacija Softvera d.o.o. nor the names of its
      contributors may be used to endorse or promote products derived from this
      software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.jsoniter;

import java.io.IOException;

class IterImplNumber {
    
    final static int[] digits = new int[256];
    final static int[] zeroToNineDigits = new int[256];
    final static int END_OF_NUMBER = -2;
    final static int DOT_IN_NUMBER = -3;
    final static int INVALID_CHAR_FOR_NUMBER = -1;
    private static final int POW10[] = {1, 10, 100, 1000, 10000, 100000, 1000000};

    static {
        for (int i = 0; i < digits.length; i++) {
            digits[i] = INVALID_CHAR_FOR_NUMBER;
            zeroToNineDigits[i] = INVALID_CHAR_FOR_NUMBER;
        }
        for (int i = '0'; i <= '9'; ++i) {
            digits[i] = (i - '0');
            zeroToNineDigits[i] = (i - '0');
        }
        for (int i = 'a'; i <= 'f'; ++i) {
            digits[i] = ((i - 'a') + 10);
        }
        for (int i = 'A'; i <= 'F'; ++i) {
            digits[i] = ((i - 'A') + 10);
        }
        zeroToNineDigits[','] = END_OF_NUMBER;
        zeroToNineDigits[']'] = END_OF_NUMBER;
        zeroToNineDigits['}'] = END_OF_NUMBER;
        zeroToNineDigits[' '] = END_OF_NUMBER;
        zeroToNineDigits['.'] = DOT_IN_NUMBER;
    }

    public static final double readDouble(JsonIterator iter) throws IOException {
        final byte c = IterImpl.nextToken(iter);
        // when re-read using slowpath, it should include the first byte
        iter.unreadByte();
        if (c == '-') {
            // skip '-' by + 1
            return readNegativeDouble(iter, iter.head + 1);
        }
        return readPositiveDouble(iter, iter.head);
    }

    private static final double readPositiveDouble(JsonIterator iter, int start) throws IOException {
        long value = 0;
        byte c = ' ';
        int i = start;
        for (; i < iter.tail; i++) {
            c = iter.buf[i];
            if (c == ',' || c == '}' || c == ']' || c == ' ') {
                iter.head = i;
                return value;
            }
            if (c == '.') break;
            final int ind = digits[c];
            value = (value << 3) + (value << 1) + ind;
            if (ind < 0 || ind > 9) {
                return readDoubleSlowPath(iter);
            }
        }
        if (c == '.') {
            i++;
            long div = 1;
            for (; i < iter.tail; i++) {
                c = iter.buf[i];
                if (c == ',' || c == '}' || c == ']' || c == ' ') {
                    iter.head = i;
                    return value / (double) div;
                }
                final int ind = digits[c];
                div = (div << 3) + (div << 1);
                value = (value << 3) + (value << 1) + ind;
                if (ind < 0 || ind > 9) {
                    return readDoubleSlowPath(iter);
                }
            }
        }
        return readDoubleSlowPath(iter);
    }

    private static final double readNegativeDouble(JsonIterator iter, int start) throws IOException {
        long value = 0;
        byte c = ' ';
        int i = start;
        for (; i < iter.tail; i++) {
            c = iter.buf[i];
            if (c == ',' || c == '}' || c == ']' || c == ' ') {
                iter.head = i;
                return value;
            }
            if (c == '.') break;
            final int ind = digits[c];
            value = (value << 3) + (value << 1) - ind;
            if (ind < 0 || ind > 9) {
                return readDoubleSlowPath(iter);
            }
        }
        if (c == '.') {
            i++;
            long div = 1;
            for (; i < iter.tail; i++) {
                c = iter.buf[i];
                if (c == ',' || c == '}' || c == ']' || c == ' ') {
                    iter.head = i;
                    return value / (double) div;
                }
                final int ind = digits[c];
                div = (div << 3) + (div << 1);
                value = (value << 3) + (value << 1) - ind;
                if (ind < 0 || ind > 9) {
                    return readDoubleSlowPath(iter);
                }
            }
        }
        return readDoubleSlowPath(iter);
    }

    public static final double readDoubleSlowPath(JsonIterator iter) throws IOException {
        try {
            return Double.valueOf(readNumber(iter));
        } catch (NumberFormatException e) {
            throw iter.reportError("readDoubleSlowPath", e.toString());
        }
    }

    public static final float readFloat(JsonIterator iter) throws IOException {
        final byte c = IterImpl.nextToken(iter);
        if (c == '-') {
            return -readPositiveFloat(iter);
        } else {
            iter.unreadByte();
            return readPositiveFloat(iter);
        }
    }

    private final static long SAFE_TO_MULTIPLY_10 = (Long.MAX_VALUE / 10) - 10;

    private static final float readPositiveFloat(JsonIterator iter) throws IOException {
        long value = 0; // without the dot
        byte c = ' ';
        int i = iter.head;
        non_decimal_loop:
        for (; i < iter.tail; i++) {
            c = iter.buf[i];
            final int ind = zeroToNineDigits[c];
            switch (ind) {
                case INVALID_CHAR_FOR_NUMBER:
                    return readFloatSlowPath(iter);
                case END_OF_NUMBER:
                    iter.head = i;
                    return value;
                case DOT_IN_NUMBER:
                    break non_decimal_loop;
            }
            if (value > SAFE_TO_MULTIPLY_10) {
                return readFloatSlowPath(iter);
            }
            value = (value << 3) + (value << 1) + ind; // value = value * 10 + ind;
        }
        if (c == '.') {
            i++;
            int decimalPlaces = 0;
            for (; i < iter.tail; i++) {
                c = iter.buf[i];
                final int ind = zeroToNineDigits[c];
                switch (ind) {
                    case END_OF_NUMBER:
                        if (decimalPlaces > 0 && decimalPlaces < POW10.length) {
                            iter.head = i;
                            return value / (float) POW10[decimalPlaces];
                        }
                        // too many decimal places
                        return readFloatSlowPath(iter);
                    case INVALID_CHAR_FOR_NUMBER:
                    case DOT_IN_NUMBER:
                        return readFloatSlowPath(iter);
                }
                decimalPlaces++;
                if (value > SAFE_TO_MULTIPLY_10) {
                    return readFloatSlowPath(iter);
                }
                value = (value << 3) + (value << 1) + ind; // value = value * 10 + ind;
            }
        }
        return readFloatSlowPath(iter);
    }

    public static final float readFloatSlowPath(JsonIterator iter) throws IOException {
        try {
            return Float.valueOf(readNumber(iter));
        } catch (NumberFormatException e) {
            throw iter.reportError("readDoubleSlowPath", e.toString());
        }
    }

    public static final String readNumber(JsonIterator iter) throws IOException {
        int j = 0;
        for (byte c = IterImpl.nextToken(iter); ; c = IterImpl.readByte(iter)) {
            if (j == iter.reusableChars.length) {
                char[] newBuf = new char[iter.reusableChars.length * 2];
                System.arraycopy(iter.reusableChars, 0, newBuf, 0, iter.reusableChars.length);
                iter.reusableChars = newBuf;
            }
            switch (c) {
                case '-':
                case '.':
                case 'e':
                case 'E':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    iter.reusableChars[j++] = (char) c;
                    break;
                case 0:
                    return new String(iter.reusableChars, 0, j);
                default:
                    iter.unreadByte();
                    return new String(iter.reusableChars, 0, j);
            }
        }
    }

    public static final int readInt(JsonIterator iter) throws IOException {
        byte c = IterImpl.nextToken(iter);
        if (c == '-') {
            return -readUnsignedInt(iter);
        } else {
            iter.unreadByte();
            return readUnsignedInt(iter);
        }
    }

    public static final int readUnsignedInt(JsonIterator iter) throws IOException {
        // TODO: throw overflow
        byte c = IterImpl.readByte(iter);
        int v = digits[c];
        if (v == 0) {
            return 0;
        }
        if (v == -1) {
            throw iter.reportError("readUnsignedInt", "expect 0~9");
        }
        int result = 0;
        for (; ; ) {
            result = result * 10 + v;
            c = IterImpl.readByte(iter);
            v = digits[c];
            if (v == -1) {
                iter.unreadByte();
                break;
            }
        }
        return result;
    }

    public static final long readLong(JsonIterator iter) throws IOException {
        byte c = IterImpl.nextToken(iter);
        if (c == '-') {
            return -readUnsignedLong(iter);
        } else {
            iter.unreadByte();
            return readUnsignedLong(iter);
        }
    }

    public static final long readUnsignedLong(JsonIterator iter) throws IOException {
        // TODO: throw overflow
        byte c = IterImpl.readByte(iter);
        int v = digits[c];
        if (v == 0) {
            return 0;
        }
        if (v == -1) {
            throw iter.reportError("readUnsignedLong", "expect 0~9");
        }
        long result = 0;
        for (; ; ) {
            result = result * 10 + v;
            c = IterImpl.readByte(iter);
            v = digits[c];
            if (v == -1) {
                iter.unreadByte();
                break;
            }
        }
        return result;
    }

    public static final char readU4(JsonIterator iter) throws IOException {
        int v = digits[IterImpl.readByte(iter)];
        if (v == -1) {
            throw iter.reportError("readU4", "bad unicode");
        }
        char b = (char) v;
        v = digits[IterImpl.readByte(iter)];
        if (v == -1) {
            throw iter.reportError("readU4", "bad unicode");
        }
        b = (char) (b << 4);
        b += v;
        v = digits[IterImpl.readByte(iter)];
        if (v == -1) {
            throw iter.reportError("readU4", "bad unicode");
        }
        b = (char) (b << 4);
        b += v;
        v = digits[IterImpl.readByte(iter)];
        if (v == -1) {
            throw iter.reportError("readU4", "bad unicode");
        }
        b = (char) (b << 4);
        b += v;
        return b;
    }
}
