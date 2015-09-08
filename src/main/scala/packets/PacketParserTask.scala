import java.io.PrintWriter
import java.util.concurrent.LinkedBlockingQueue
import jssc.SerialPortException
import org.openbci.packets.OpenBCIPacketV3

package org.openbci.packets {
  class PacketParserTask(rawEvents: LinkedBlockingQueue[Array[Byte]], writer: PrintWriter)
  extends Runnable {
    def run {
      try {
        while(!Thread.currentThread.isInterrupted) {
          val rawPacket: Array[Byte] = rawEvents.take
          try {
            val packet = OpenBCIPacketV3(rawPacket)
            writer.println(packet)
            writer.flush
          }
          catch {
            case e: IllegalArgumentException => println("Unable to read packet: " + new String(rawPacket))
          }
        }
      } catch {
        case e: InterruptedException     => Thread.currentThread.interrupt
        case e: SerialPortException      => Thread.currentThread.interrupt; throw e
      }
    }
  }
}
