package org.rafalohaki.ultramotd.util;

import io.netty.buffer.ByteBuf;

/**
 * VarInt utilities for Minecraft protocol packet encoding.
 * Supports variable-length integer encoding used in status packets.
 */
public final class VarInts {
    
    private VarInts() {}

    /**
     * Calculate the size in bytes needed to encode a value as VarInt
     */
    public static int sizeOf(int value) {
        int size = 0;
        do {
            value >>>= 7;
            size++;
        } while (value != 0);
        return size;
    }

    /**
     * Write a VarInt to ByteBuf
     */
    public static void writeVarInt(ByteBuf out, int value) {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    /**
     * Read a VarInt from ByteBuf
     */
    public static int readVarInt(ByteBuf in) {
        int value = 0;
        int position = 0;
        byte currentByte;
        
        do {
            currentByte = in.readByte();
            value |= (currentByte & 0x7F) << position;
            
            if (position >= 32) {
                throw new RuntimeException("VarInt is too big");
            }
            
            position += 7;
        } while ((currentByte & 0x80) != 0);
        
        return value;
    }
}
