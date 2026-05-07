package com.example.schemas;

import java.io.Serializable;
import java.nio.ByteBuffer;

public interface BaseStruct extends Serializable {
    void toByteArray(ByteBuffer byteBuffer);
    void fromByteArray(ByteBuffer byteBuffer);
}
