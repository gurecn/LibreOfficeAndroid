package org.libreoffice.kit;

import java.nio.ByteBuffer;

/**
 * This is the common code for allocation and freeing of memory. For this direct ByteBuffer is used but
 * in the past it was possible to use a JNI version of allocation because of a bug in old Android version.
 */
public final class DirectBufferAllocator {

    private static final String LOGTAG = DirectBufferAllocator.class.getSimpleName();

    private DirectBufferAllocator() {
    }

    public static ByteBuffer allocate(int size) {
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(size);
        if (directBuffer == null) {
            if (size <= 0) {
                throw new IllegalArgumentException("Invalid allocation size: " + size);
            } else {
                throw new OutOfMemoryError("allocateDirectBuffer() returned null");
            }
        } else if (!directBuffer.isDirect()) {
            throw new AssertionError("allocateDirectBuffer() did not return a direct buffer");
        }

        return directBuffer;
    }

    public static ByteBuffer free(ByteBuffer buffer) {
        if (buffer == null) {
            return null;
        }

        if (!buffer.isDirect()) {
            throw new IllegalArgumentException("ByteBuffer must be direct");
        }
        // can't free buffer - leave this to the VM
        return null;
    }

    public static ByteBuffer guardedAllocate(int size) {
        ByteBuffer buffer = null;
        try {
            buffer = allocate(size);
        } catch (OutOfMemoryError oomException) {
            return null;
        }
        return buffer;
    }
}
