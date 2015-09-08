/* Imports from the RXTX Serial Communications library. */
import java.lang.IllegalArgumentException

package org.openbci.packets {

  object OpenBCIPacketV3 {
    def apply(raw: Array[Byte]) = new OpenBCIPacketV3(raw)
  }

  @throws(classOf[IllegalArgumentException])
  class OpenBCIPacketV3(private val raw: Array[Byte]) {
    if(raw.size != 33)
      throw new IllegalArgumentException("An OpenBCI V3 Packet consists of 33 bytes.")
    val header: Byte     = raw(0)
    val sample: Int      = raw(1) & 0xFF
    val eegChannel1: Int = Int24BEToInt32Native(raw.slice(2,5))
    val eegChannel2: Int = Int24BEToInt32Native(raw.slice(5,8))
    val eegChannel3: Int = Int24BEToInt32Native(raw.slice(8,11))
    val eegChannel4: Int = Int24BEToInt32Native(raw.slice(11,14))
    val eegChannel5: Int = Int24BEToInt32Native(raw.slice(14,17))
    val eegChannel6: Int = Int24BEToInt32Native(raw.slice(17,20))
    val eegChannel7: Int = Int24BEToInt32Native(raw.slice(20,23))
    val eegChannel8: Int = Int24BEToInt32Native(raw.slice(23,26))

    val accelX: Int      = Int16BEToInt32Native(raw.slice(26,28))
    val accelY: Int      = Int16BEToInt32Native(raw.slice(28,30))
    val accelZ: Int      = Int16BEToInt32Native(raw.slice(30,32))
    val footer: Byte     = raw(32)

    if(header != 0xA0.toByte || footer != 0xC0.toByte)
       throw new IllegalArgumentException("Header (" + header + ") or footer (" + footer + ") is invalid.")

    override def equals(e: Any): Boolean =
      e match {
        case p: OpenBCIPacketV3 => raw.equals(p.raw)
        case _ => false
      }

    override def toString: String =
      List(sample,
        eegChannel1, eegChannel2, eegChannel3, eegChannel4,
        eegChannel5, eegChannel6, eegChannel7, eegChannel8,
        accelX, accelY, accelZ) mkString ", "
  }
}
