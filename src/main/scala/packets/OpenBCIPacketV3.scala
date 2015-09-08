/* Imports from the RXTX Serial Communications library. */
import java.lang.IllegalArgumentException
import org.openbci.ADS1299

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

    // NB. These raw integer values are in 'counts,' which should be converted to microvolts.
    // Cf. http://docs.openbci.com/software/02-OpenBCI_Streaming_Data_Format#openbci-v3-data-format-interpreting-the-eeg-data
    val eegChannel1: Float = ADS1299.scaleCountToMicrovolts(Int24BEToInt32Native(raw.slice(2,   5)))
    val eegChannel2: Float = ADS1299.scaleCountToMicrovolts(Int24BEToInt32Native(raw.slice(5,   8)))
    val eegChannel3: Float = ADS1299.scaleCountToMicrovolts(Int24BEToInt32Native(raw.slice(8,  11)))
    val eegChannel4: Float = ADS1299.scaleCountToMicrovolts(Int24BEToInt32Native(raw.slice(11, 14)))
    val eegChannel5: Float = ADS1299.scaleCountToMicrovolts(Int24BEToInt32Native(raw.slice(14, 17)))
    val eegChannel6: Float = ADS1299.scaleCountToMicrovolts(Int24BEToInt32Native(raw.slice(17, 20)))
    val eegChannel7: Float = ADS1299.scaleCountToMicrovolts(Int24BEToInt32Native(raw.slice(20, 23)))
    val eegChannel8: Float = ADS1299.scaleCountToMicrovolts(Int24BEToInt32Native(raw.slice(23, 26)))

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
      List(sample.toString,
        eegChannel1, eegChannel2, eegChannel3, eegChannel4,
        eegChannel5, eegChannel6, eegChannel7, eegChannel8,
        accelX.toString, accelY.toString, accelZ.toString) mkString ", "
  }
}
