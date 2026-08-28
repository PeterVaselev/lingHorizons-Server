package me.ling.horizons.common.util;

import sun.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;

public class UnsafeUtil {
    public static final Unsafe UNSAFE;
    private static final MethodHandle DIRECT_BYTE_BUFFER_CTOR;

    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            UNSAFE = (Unsafe) field.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        MethodHandle ctorHandle = null;
        try {
            Class<?> dbbClass = Class.forName("java.nio.DirectByteBuffer");
            Constructor<?> ctor = dbbClass.getDeclaredConstructor(long.class, int.class);
            ctor.setAccessible(true);
            ctorHandle = MethodHandles.lookup().unreflectConstructor(ctor);
        } catch (Throwable ignored) {}
        DIRECT_BYTE_BUFFER_CTOR = ctorHandle;
    }

    private static final long BYTE_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
    private static final long SHORT_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(short[].class);

    public static long allocateMemory(long bytes) {
        return UNSAFE.allocateMemory(bytes);
    }

    public static void freeMemory(long address) {
        UNSAFE.freeMemory(address);
    }

    public static void setMemory(long address, long bytes, byte value) {
        UNSAFE.setMemory(address, bytes, value);
    }

    public static void putLong(long address, long value) {
        UNSAFE.putLong(address, value);
    }

    public static long getLong(long address) {
        return UNSAFE.getLong(address);
    }

    public static void putInt(long address, int value) {
        UNSAFE.putInt(address, value);
    }

    public static int getInt(long address) {
        return UNSAFE.getInt(address);
    }

    public static void putShort(long address, short value) {
        UNSAFE.putShort(address, value);
    }

    public static short getShort(long address) {
        return UNSAFE.getShort(address);
    }

    public static void putByte(long address, byte value) {
        UNSAFE.putByte(address, value);
    }

    public static byte getByte(long address) {
        return UNSAFE.getByte(address);
    }

    public static ByteBuffer directByteBuffer(long address, int capacity) {
        if (DIRECT_BYTE_BUFFER_CTOR != null) {
            try {
                return (ByteBuffer) DIRECT_BYTE_BUFFER_CTOR.invoke(address, capacity);
            } catch (Throwable ignored) {}
        }
        ByteBuffer buf = ByteBuffer.allocateDirect(capacity);
        return buf;
    }

    public static void memcpy(long src, long dst, long length) {
        UNSAFE.copyMemory(src, dst, length);
    }

    //Copy the entire length of src to the dst memory where dst is a byte array (source length from dst)
    public static void memcpy(long src, byte[] dst) {
        UNSAFE.copyMemory(null, src, dst, BYTE_ARRAY_BASE_OFFSET, dst.length);
    }

    public static void memcpy(long src, int length, byte[] dst) {
        UNSAFE.copyMemory(null, src, dst, BYTE_ARRAY_BASE_OFFSET, length);
    }

    public static void memcpy(long src, int length, byte[] dst, int offset) {
        UNSAFE.copyMemory(null, src, dst, BYTE_ARRAY_BASE_OFFSET + offset, length);
    }

    //Copy the entire length of src to the dst memory where src is a byte array (source length from src)
    public static void memcpy(byte[] src, long dst) {
        UNSAFE.copyMemory(src, BYTE_ARRAY_BASE_OFFSET, null, dst, src.length);
    }

    public static void memcpy(byte[] src, int len, long dst) {
        UNSAFE.copyMemory(src, BYTE_ARRAY_BASE_OFFSET, null, dst, len);
    }

    public static void memcpy(short[] src, long dst) {
        UNSAFE.copyMemory(src, SHORT_ARRAY_BASE_OFFSET, null, dst, (long) src.length << 1);
    }
}
