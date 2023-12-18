/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.bytecode;

import java.lang.reflect.Field;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.impl.FrameWithoutBoxing;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

import sun.misc.Unsafe;

/**
 * Accessor class used to abstract away frame and bytecode array accesses in the generated code.
 *
 * Do not use directly.
 */
public abstract sealed class BytecodeDSLAccess permits BytecodeDSLAccess.SafeImpl, BytecodeDSLAccess.UnsafeImpl {

    public static volatile BytecodeDSLAccess safeSingleton;
    public static volatile BytecodeDSLAccess unsafeSingleton;

    /**
     * Accessor methods for generated bytecode DSL interpreters. Do not use directly.
     */
    public static final BytecodeDSLAccess lookup(Object token, boolean allowUnsafe) {
        if (token != BytecodeNodes.TOKEN) {
            throw new IllegalArgumentException("Invalid usage token. Seriously, you shouldn't use this.");
        }

        BytecodeDSLAccess impl;
        if (allowUnsafe && !Boolean.getBoolean("truffle.dsl.DisableUnsafeBytecodeDSLAccess")) {
            impl = unsafeSingleton;
            if (impl == null) {
                impl = unsafeSingleton = createUnsafe();
            }
        } else {
            impl = safeSingleton;
            if (impl == null) {
                impl = safeSingleton = createSafe();
            }
        }
        return impl;
    }

    private BytecodeDSLAccess() {
    }

    private static BytecodeDSLAccess createSafe() {
        return new SafeImpl();
    }

    private static BytecodeDSLAccess createUnsafe() {
        return new UnsafeImpl();
    }

    public abstract short shortArrayRead(short[] arr, int index);

    public abstract void shortArrayWrite(short[] arr, int index, short value);

    public abstract byte byteArrayRead(byte[] arr, int index);

    public abstract void byteArrayWrite(byte[] arr, int index, byte value);

    public abstract int intArrayRead(int[] arr, int index);

    public abstract void intArrayWrite(int[] arr, int index, int value);

    public abstract <T> T objectArrayRead(T[] arr, int index);

    public abstract <T> T cast(Object arr, Class<T> clazz);

    public abstract byte getTag(Frame frame, int slot);

    public abstract Object getObject(Frame frame, int slot);

    public abstract boolean getBoolean(Frame frame, int slot);

    public abstract int getInt(Frame frame, int slot);

    public abstract long getLong(Frame frame, int slot);

    public abstract byte getByte(Frame frame, int slot);

    public abstract float getFloat(Frame frame, int slot);

    public abstract double getDouble(Frame frame, int slot);

    public abstract Object uncheckedGetObject(Frame frame, int slot);

    public abstract boolean uncheckedGetBoolean(Frame frame, int slot);

    public abstract byte uncheckedGetByte(Frame frame, int slot);

    public abstract int uncheckedGetInt(Frame frame, int slot);

    public abstract long uncheckedGetLong(Frame frame, int slot);

    public abstract float uncheckedGetFloat(Frame frame, int slot);

    public abstract double uncheckedGetDouble(Frame frame, int slot);

    public abstract void uncheckedSetObject(Frame frame, int slot, Object value);

    @SuppressWarnings("static-method")
    public final Object getValue(Frame frame, int slot) {
        return frame.getValue(slot);
    }

    public abstract boolean expectBoolean(Frame frame, int slot) throws UnexpectedResultException;

    public abstract byte expectByte(Frame frame, int slot) throws UnexpectedResultException;

    public abstract int expectInt(Frame frame, int slot) throws UnexpectedResultException;

    public abstract long expectLong(Frame frame, int slot) throws UnexpectedResultException;

    public abstract Object expectObject(Frame frame, int slot) throws UnexpectedResultException;

    public abstract float expectFloat(Frame frame, int slot) throws UnexpectedResultException;

    public abstract double expectDouble(Frame frame, int slot) throws UnexpectedResultException;

    public final Object requireObject(Frame frame, int slot) {
        try {
            return expectObject(frame, slot);
        } catch (UnexpectedResultException e) {
            return e.getResult();
        }
    }

    public abstract void setObject(Frame frame, int slot, Object value);

    public abstract void setBoolean(Frame frame, int slot, boolean value);

    public abstract void setInt(Frame frame, int slot, int value);

    public abstract void setLong(Frame frame, int slot, long value);

    public abstract void setDouble(Frame frame, int slot, double value);

    public abstract void copy(Frame frame, int srcSlot, int dstSlot);

    public abstract void copyTo(Frame srcFrame, int srcOffset, Frame dstFrame, int dstOffset, int length);

    public abstract void copyObject(Frame frame, int srcSlot, int dstSlot);

    public abstract void copyPrimitive(Frame frame, int srcSlot, int dstSlot);

    public abstract void clear(Frame frame, int slot);

    static final class UnsafeImpl extends BytecodeDSLAccess {

        static final Unsafe UNSAFE = initUnsafe();

        private static Unsafe initUnsafe() {
            try {
                // Fast path when we are trusted.
                return Unsafe.getUnsafe();
            } catch (SecurityException se) {
                // Slow path when we are not trusted.
                try {
                    Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                    theUnsafe.setAccessible(true);
                    return (Unsafe) theUnsafe.get(Unsafe.class);
                } catch (Exception e) {
                    throw new RuntimeException("exception while trying to get Unsafe", e);
                }
            }
        }

        @Override
        public short shortArrayRead(short[] arr, int index) {
            assert index >= 0 && index < arr.length;
            return UNSAFE.getShort(arr, Unsafe.ARRAY_SHORT_BASE_OFFSET + index * Unsafe.ARRAY_SHORT_INDEX_SCALE);
        }

        @Override
        public void shortArrayWrite(short[] arr, int index, short value) {
            assert index >= 0 && index < arr.length;
            UNSAFE.putShort(arr, Unsafe.ARRAY_SHORT_BASE_OFFSET + index * Unsafe.ARRAY_SHORT_INDEX_SCALE, value);
        }

        @Override
        public byte byteArrayRead(byte[] arr, int index) {
            assert index >= 0 && index < arr.length;
            return UNSAFE.getByte(arr, Unsafe.ARRAY_BYTE_BASE_OFFSET + index * Unsafe.ARRAY_BYTE_INDEX_SCALE);
        }

        @Override
        public void byteArrayWrite(byte[] arr, int index, byte value) {
            assert index >= 0 && index < arr.length;
            UNSAFE.putByte(arr, Unsafe.ARRAY_BYTE_BASE_OFFSET + index * Unsafe.ARRAY_BYTE_INDEX_SCALE, value);
        }

        @Override
        public int intArrayRead(int[] arr, int index) {
            assert index >= 0 && index < arr.length;
            return UNSAFE.getInt(arr, Unsafe.ARRAY_INT_BASE_OFFSET + index * Unsafe.ARRAY_INT_INDEX_SCALE);
        }

        @Override
        public void intArrayWrite(int[] arr, int index, int value) {
            assert index >= 0 && index < arr.length;
            UNSAFE.putInt(arr, Unsafe.ARRAY_INT_BASE_OFFSET + index * Unsafe.ARRAY_INT_INDEX_SCALE, value);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T objectArrayRead(T[] arr, int index) {
            assert index >= 0 && index < arr.length;
            return (T) UNSAFE.getObject(arr, Unsafe.ARRAY_OBJECT_BASE_OFFSET + index * Unsafe.ARRAY_OBJECT_INDEX_SCALE);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T cast(Object obj, Class<T> clazz) {
            // TODO: make this unsafer
            return (T) obj;
        }

        @Override
        public byte getTag(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeGetTag(slot);
        }

        @Override
        public Object getObject(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeGetObject(slot);
        }

        @Override
        public int getInt(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeGetInt(slot);
        }

        @Override
        public boolean getBoolean(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeGetBoolean(slot);
        }

        @Override
        public long getLong(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeGetLong(slot);
        }

        @Override
        public double getDouble(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeGetDouble(slot);
        }

        @Override
        public byte getByte(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeGetByte(slot);
        }

        @Override
        public float getFloat(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeGetFloat(slot);
        }

        @Override
        public Object uncheckedGetObject(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeUncheckedGetObject(slot);
        }

        @Override
        public byte uncheckedGetByte(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeUncheckedGetByte(slot);
        }

        @Override
        public int uncheckedGetInt(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeUncheckedGetInt(slot);
        }

        @Override
        public boolean uncheckedGetBoolean(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeUncheckedGetBoolean(slot);
        }

        @Override
        public long uncheckedGetLong(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeUncheckedGetLong(slot);
        }

        @Override
        public double uncheckedGetDouble(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeUncheckedGetDouble(slot);
        }

        @Override
        public float uncheckedGetFloat(Frame frame, int slot) {
            return ((FrameWithoutBoxing) frame).unsafeUncheckedGetFloat(slot);
        }

        @Override
        public void setObject(Frame frame, int slot, Object value) {
            ((FrameWithoutBoxing) frame).unsafeSetObject(slot, value);
        }

        @Override
        public void setInt(Frame frame, int slot, int value) {
            ((FrameWithoutBoxing) frame).unsafeSetInt(slot, value);
        }

        @Override
        public void setBoolean(Frame frame, int slot, boolean value) {
            ((FrameWithoutBoxing) frame).unsafeSetBoolean(slot, value);
        }

        @Override
        public void setLong(Frame frame, int slot, long value) {
            ((FrameWithoutBoxing) frame).unsafeSetLong(slot, value);
        }

        @Override
        public void setDouble(Frame frame, int slot, double value) {
            ((FrameWithoutBoxing) frame).unsafeSetDouble(slot, value);
        }

        @Override
        public void copy(Frame frame, int srcSlot, int dstSlot) {
            ((FrameWithoutBoxing) frame).unsafeCopy(srcSlot, dstSlot);
        }

        @Override
        public void copyTo(Frame srcFrame, int srcOffset, Frame dstFrame, int dstOffset, int length) {
            ((FrameWithoutBoxing) srcFrame).unsafeCopyTo(srcOffset, ((FrameWithoutBoxing) dstFrame), dstOffset, length);
        }

        @Override
        public void copyObject(Frame frame, int srcSlot, int dstSlot) {
            ((FrameWithoutBoxing) frame).unsafeCopyObject(srcSlot, dstSlot);
        }

        @Override
        public void copyPrimitive(Frame frame, int srcSlot, int dstSlot) {
            ((FrameWithoutBoxing) frame).unsafeCopyPrimitive(srcSlot, dstSlot);
        }

        @Override
        public void clear(Frame frame, int slot) {
            ((FrameWithoutBoxing) frame).unsafeClear(slot);
        }

        @Override
        public void uncheckedSetObject(Frame frame, int slot, Object value) {
            ((FrameWithoutBoxing) frame).unsafeUncheckedSetObject(slot, value);
        }

        @Override
        public boolean expectBoolean(Frame frame, int slot) throws UnexpectedResultException {
            return ((FrameWithoutBoxing) frame).unsafeExpectBoolean(slot);
        }

        @Override
        public byte expectByte(Frame frame, int slot) throws UnexpectedResultException {
            return ((FrameWithoutBoxing) frame).unsafeExpectByte(slot);
        }

        @Override
        public int expectInt(Frame frame, int slot) throws UnexpectedResultException {
            return ((FrameWithoutBoxing) frame).unsafeExpectInt(slot);
        }

        @Override
        public long expectLong(Frame frame, int slot) throws UnexpectedResultException {
            return ((FrameWithoutBoxing) frame).unsafeExpectLong(slot);
        }

        @Override
        public Object expectObject(Frame frame, int slot) throws UnexpectedResultException {
            return ((FrameWithoutBoxing) frame).unsafeExpectObject(slot);
        }

        @Override
        public float expectFloat(Frame frame, int slot) throws UnexpectedResultException {
            return ((FrameWithoutBoxing) frame).unsafeExpectFloat(slot);
        }

        @Override
        public double expectDouble(Frame frame, int slot) throws UnexpectedResultException {
            return ((FrameWithoutBoxing) frame).unsafeExpectDouble(slot);
        }

    }

    static final class SafeImpl extends BytecodeDSLAccess {

        @Override
        public short shortArrayRead(short[] arr, int index) {
            return arr[index];
        }

        @Override
        public void shortArrayWrite(short[] arr, int index, short value) {
            arr[index] = value;
        }

        @Override
        public byte byteArrayRead(byte[] arr, int index) {
            return arr[index];
        }

        @Override
        public void byteArrayWrite(byte[] arr, int index, byte value) {
            arr[index] = value;
        }

        @Override
        public int intArrayRead(int[] arr, int index) {
            return arr[index];
        }

        @Override
        public void intArrayWrite(int[] arr, int index, int value) {
            arr[index] = value;
        }

        @Override
        public <T> T objectArrayRead(T[] arr, int index) {
            return arr[index];
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T cast(Object obj, Class<T> clazz) {
            return (T) obj;
        }

        @Override
        public byte getTag(Frame frame, int slot) {
            return frame.getTag(slot);
        }

        @Override
        public Object getObject(Frame frame, int slot) {
            return frame.getObject(slot);
        }

        @Override
        public boolean getBoolean(Frame frame, int slot) {
            return frame.getBoolean(slot);
        }

        @Override
        public int getInt(Frame frame, int slot) {
            return frame.getInt(slot);
        }

        @Override
        public long getLong(Frame frame, int slot) {
            return frame.getLong(slot);
        }

        @Override
        public double getDouble(Frame frame, int slot) {
            return frame.getDouble(slot);
        }

        @Override
        public byte getByte(Frame frame, int slot) {
            return frame.getByte(slot);
        }

        @Override
        public float getFloat(Frame frame, int slot) {
            return frame.getFloat(slot);
        }

        @Override
        public Object uncheckedGetObject(Frame frame, int slot) {
            return frame.getObject(slot);
        }

        @Override
        public boolean uncheckedGetBoolean(Frame frame, int slot) {
            return frame.getBoolean(slot);
        }

        @Override
        public byte uncheckedGetByte(Frame frame, int slot) {
            return frame.getByte(slot);
        }

        @Override
        public int uncheckedGetInt(Frame frame, int slot) {
            return frame.getInt(slot);
        }

        @Override
        public long uncheckedGetLong(Frame frame, int slot) {
            return frame.getLong(slot);
        }

        @Override
        public double uncheckedGetDouble(Frame frame, int slot) {
            return frame.getDouble(slot);
        }

        @Override
        public float uncheckedGetFloat(Frame frame, int slot) {
            return frame.getFloat(slot);
        }

        @Override
        public boolean expectBoolean(Frame frame, int slot) throws UnexpectedResultException {
            return frame.expectBoolean(slot);
        }

        @Override
        public byte expectByte(Frame frame, int slot) throws UnexpectedResultException {
            return frame.expectByte(slot);
        }

        @Override
        public int expectInt(Frame frame, int slot) throws UnexpectedResultException {
            return frame.expectInt(slot);
        }

        @Override
        public long expectLong(Frame frame, int slot) throws UnexpectedResultException {
            return frame.expectLong(slot);
        }

        @Override
        public Object expectObject(Frame frame, int slot) throws UnexpectedResultException {
            return frame.expectObject(slot);
        }

        @Override
        public float expectFloat(Frame frame, int slot) throws UnexpectedResultException {
            return frame.expectFloat(slot);
        }

        @Override
        public double expectDouble(Frame frame, int slot) throws UnexpectedResultException {
            return frame.expectDouble(slot);
        }

        @Override
        public void setObject(Frame frame, int slot, Object value) {
            frame.setObject(slot, value);
        }

        @Override
        public void setBoolean(Frame frame, int slot, boolean value) {
            frame.setBoolean(slot, value);
        }

        @Override
        public void setInt(Frame frame, int slot, int value) {
            frame.setInt(slot, value);
        }

        @Override
        public void setLong(Frame frame, int slot, long value) {
            frame.setLong(slot, value);
        }

        @Override
        public void setDouble(Frame frame, int slot, double value) {
            frame.setDouble(slot, value);
        }

        @Override
        public void copy(Frame frame, int srcSlot, int dstSlot) {
            frame.copy(srcSlot, dstSlot);
        }

        @Override
        public void copyTo(Frame srcFrame, int srcOffset, Frame dstFrame, int dstOffset, int length) {
            srcFrame.copyTo(srcOffset, dstFrame, dstOffset, length);
        }

        @Override
        public void copyObject(Frame frame, int srcSlot, int dstSlot) {
            frame.copyObject(srcSlot, dstSlot);
        }

        @Override
        public void copyPrimitive(Frame frame, int srcSlot, int dstSlot) {
            frame.copyPrimitive(srcSlot, dstSlot);
        }

        @Override
        public void clear(Frame frame, int slot) {
            frame.clear(slot);
        }

        @Override
        public void uncheckedSetObject(Frame frame, int slot, Object value) {
            frame.setObject(slot, value);
        }

    }
}
