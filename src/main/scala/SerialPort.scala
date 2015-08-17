package org.OpenBCI.SerialPort {
  import scala.io.StdIn
  import scala.util.{
    Try,
    Success,
    Failure
  }
  import java.lang.Thread.sleep
  import scala.collection.mutable.{
    HashSet,
    HashMap
  }
  import java.io.{FileDescriptor,
    IOException,
    InputStream,
    OutputStream
  }
  import jssc.{
    SerialPort, 
    SerialPortList,
    SerialPortEvent,
    SerialPortEventListener,
    SerialPortException
  }
  import java.lang.{
    NumberFormatException,
    ArrayIndexOutOfBoundsException,
    IllegalArgumentException
  }

  object RFDuinoUSBDongle {
    final val baud = SerialPort.BAUDRATE_115200
    final val dataBits = SerialPort.DATABITS_8
    final val stopBits = SerialPort.STOPBITS_1
    final val parity = SerialPort.PARITY_NONE
  }

  /* Wild, wacky Scala singleton static members! */
 object SerialPortManager {
   @throws(classOf[NumberFormatException])
   @throws(classOf[ArrayIndexOutOfBoundsException])
   def selectPortConsole() : String = {
     val portNames = SerialPortList.getPortNames
     println("Please select the Serial Port ID of an OpenBCI:")
     ((0 until portNames.length) zip portNames).foreach(
       com => println("\tID " + com._1.toString + ":\t" + com._2)
     )
     try {
       println("Enter an ID number, followed by ENTER: ")
       val idNumber = StdIn.readInt
       val serPort = portNames(idNumber)
       println("Serial Port " + serPort + " was selected.")
       return portNames(idNumber)
     } catch {
       case e: NumberFormatException =>
       val msg = "Invalid Serial Port ID specified. Did you press backspace?"
       throw new NumberFormatException(msg)

       case e: ArrayIndexOutOfBoundsException =>
       val msg = "Invalid Serial Port ID specified."
       throw new ArrayIndexOutOfBoundsException(msg)

       case e: Throwable => throw e
     }
   }
 }

 @throws(classOf[java.lang.IllegalArgumentException])
 class SerialPortManager(serPortName: String) {

   /** Class implementing the JSSC serialEvent method.
    *  Drawn from java-simple-serial-connector/wiki/jSSC_examples
    */
   class SerialPortReader(serPortName: String)
   extends SerialPortEventListener {
     private val bufSize = 256
     private val calendar = java.util.Calendar.getInstance
     private def debugLog(func: String, s: String) {
       println("SOURCE: SerialPortReader")
       println("TSTAMP: " + calendar.getTime)
       println("CALLER: " + func)
       println(s)
       println("EOM")
     }

     @throws(classOf[SerialPortException])
     def serialEvent(event: SerialPortEvent) {
       val logEvent = debugLog("serialEvent", _: String)
       def debugLogEvent(e: SerialPortEvent, eType: String) =
         logEvent(event.getPortName +
           " : " + eType + 
           " - " + event.getEventValue)

       if(event.isBREAK)            // Always 0
         debugLogEvent(event, "BREAK")
       else if(event.isRXCHAR)      // Int - Input buffer byte count
         if(bufSize <= event.getEventValue)
           Try(serialPort.readBytes(bufSize)) recover {
             case e: SerialPortException => throw e
           }
           else if(event.isRXFLAG)      // Int - Input buffer byte count
             debugLogEvent(event, "RXFLAG")
           else if(event.isTXEMPTY)     // Int - Output buffer byte count
             debugLogEvent(event, "TXEMPTY")
           else if(event.isCTS)         // Boolean - CTS Line State
             debugLogEvent(event, "CTS")
           else if(event.isDSR)         // Boolean - DSR Line State
             debugLogEvent(event, "DSR")
           else if(event.isRLSD)        // Boolean - RLSD Line State
             debugLogEvent(event, "RLSD")
           else if(event.isRING)        // Boolean - Ring Line State
             debugLogEvent(event, "RING")
           else if(event.isERR)         // Mask - 1+ Errors
             debugLogEvent(event, "ERR")
           else
             debugLogEvent(event, "UNKNOWN")
     }
   }

   private val portNames = SerialPortList.getPortNames

   /** Write a character to the serial port as a byte.
    *  OpenBCI documentation reccomends a delay after each write.
    *  @param c The character to write
    *  @return Nothing
    */
   def write(c: Char) {
     serialPort.writeByte(c.toByte)
     sleep(5, 0)
   }

   /** Write a string to the serial port as a byte array.
    *  OpenBCI documentation reccomends a delay after each write.
    *  @param c The character to write
    *  @return Nothing
    */
   def write(s: String) : Unit = s.map(write)

   /** Close the connection to the serial port and release
    *  all serial port handlers.
    *  @return Whether or not the serial port closed successfully
    */
   def close() : Boolean = 
     try { 
       serialPort.closePort // TODO: Log this Boolean value.
     } catch {
       case e: SerialPortException => throw e
     }
     /** Check if the managed serial port is open.
      *  @return True if the serial port is open.
      */
     def isOpen() : Boolean = serialPort.isOpened

     /* Avoid TYPE_PORT_NOT_FOUND SerialPortException */
    if(!portNames.contains(serPortName))
      throw new IllegalArgumentException("No such serial port: " + serPortName + ".")
    else
      ()

    private val serialPort = new SerialPort(serPortName)
    try {
      serialPort.openPort
    } catch {
      case e: SerialPortException => close; throw e
    }
    if(!serialPort.setParams(RFDuinoUSBDongle.baud,
      RFDuinoUSBDongle.dataBits,
      RFDuinoUSBDongle.stopBits,
      RFDuinoUSBDongle.parity)) {
        close
        throw new SerialPortException(serPortName, "SerialPortManager",
          "Unable to set serial port parameters.")
      }
 }
}
