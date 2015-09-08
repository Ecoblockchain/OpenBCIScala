import jssc.{
  SerialPort, 
  SerialPortEvent,
  SerialPortEventListener,
  SerialPortException
}
import java.util.logging.Logger
import java.util.concurrent.LinkedBlockingQueue

package org.openbci.serial {
 object SerialPortReader {
   private val log = Logger.getLogger("SerialPortReader")
   def apply(port: SerialPort,
     events: LinkedBlockingQueue[Array[Byte]],
     bufSize: Int = 33) = new SerialPortReader(port, events, bufSize)
 }

 class SerialPortReader(port: SerialPort,
   events: LinkedBlockingQueue[Array[Byte]],
   bufSize: Int = 33) extends SerialPortEventListener {

   def logSerialEvent(e: SerialPortEvent, eType: String) =
     SerialPortReader.log.fine(e.getPortName + ": " + eType + " - " + e.getEventValue)

   @throws(classOf[SerialPortException])
   def serialEvent(event: SerialPortEvent) {
     if(event.isBREAK)        logSerialEvent(event, "BREAK")      // Always 0
     else if(event.isRXCHAR)      
       if(bufSize <= event.getEventValue)                         // Int - Input buffer byte count
         events.put(port.readBytes(bufSize))
     else if(event.isRXFLAG)  logSerialEvent(event, "RXFLAG")     // Int - Input buffer byte count
     else if(event.isTXEMPTY) logSerialEvent(event, "TXEMPTY")    // Int - Output buffer byte count
     else if(event.isCTS)     logSerialEvent(event, "CTS")        // Boolean - CTS Line State
     else if(event.isDSR)     logSerialEvent(event, "DSR")        // Boolean - DSR Line State
     else if(event.isRLSD)    logSerialEvent(event, "RLSD")       // Boolean - RLSD Line State
     else if(event.isRING)    logSerialEvent(event, "RING")       // Boolean - Ring Line State
     else if(event.isERR)     logSerialEvent(event, "ERR")        // Mask - 1+ Errors
     else logSerialEvent(event, event.getEventType.toString)
   }
 }
}
