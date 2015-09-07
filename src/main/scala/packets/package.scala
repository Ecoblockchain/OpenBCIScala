import java.lang.IllegalArgumentException

package org.openbci {
  package object packets {

    @throws(classOf[IllegalArgumentException])
    def Int24BEToInt32Native(bytes: Array[Byte]): Int = {
      if(bytes.length == 3)
        (0xFF & bytes(0) << 16) | (0xFF & bytes(1) << 8) | (0xFF & bytes(2)) match {
          case neg if 0 < (neg & 0x00800000) => neg | 0xFF000000 
          case pos => pos & 0x00FFFFFF
        }
        else
          throw new IllegalArgumentException("An Int24 packet element must have a length of 3 bytes.")
    }

    @throws(classOf[IllegalArgumentException])
    def Int16BEToInt32Native(bytes: Array[Byte]): Int = {
      if(bytes.length == 2)
        (0xFF & bytes(0) << 8) | (0xFF & bytes(1)) match {
          case neg if 0 < (neg & 0x00008000) => neg | 0xFFFF0000 
          case pos => pos & 0x0000FFFF
        }
        else
          throw new IllegalArgumentException("An Int16 packet element must have a length of 2 bytes.")
    }
  }
}
