import scala.io.StdIn
import java.lang.Thread
import java.nio.charset.StandardCharsets
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
import java.util.logging.Logger
import org.OpenBCI.{OpenBCIPacketV3, RFDuinoUSBDongle}

package org.OpenBCI.SerialPort {
 object SerialPortReader {
   private val log = Logger.getLogger("SerialPortReader")

   def apply(port: SerialPort) = new SerialPortReader(port)
 }

 class SerialPortReader(port: SerialPort, bufSize: Int = 33) extends SerialPortEventListener {

   def logSerialEvent(e: SerialPortEvent, eType: String) =
     SerialPortReader.log.info(e.getPortName + ": " + eType + " - " + e.getEventValue)

   @throws(classOf[SerialPortException])
   def serialEvent(event: SerialPortEvent) {

     if(event.isBREAK)        logSerialEvent(event, "BREAK")    // Always 0
     else if(event.isRXCHAR)      // Int - Input buffer byte count
       if(bufSize <= event.getEventValue) try {
           val readBytes: Array[Byte] = port.readBytes(bufSize)
           println(OpenBCIPacketV3(readBytes).toString)
         } catch { case e: SerialPortException => throw e }
     else if(event.isRXFLAG)  logSerialEvent(event, "RXFLAG")     // Int - Input buffer byte count
     else if(event.isTXEMPTY) logSerialEvent(event, "TXEMPTY")    // Int - Output buffer byte count
     else if(event.isCTS)     logSerialEvent(event, "CTS")        // Boolean - CTS Line State
     else if(event.isDSR)     logSerialEvent(event, "DSR")        // Boolean - DSR Line State
     else if(event.isRLSD)    logSerialEvent(event, "RLSD")       // Boolean - RLSD Line State
     else if(event.isRING)    logSerialEvent(event, "RING")       // Boolean - Ring Line State
     else if(event.isERR)     logSerialEvent(event, "ERR")        // Mask - 1+ Errors
     else logSerialEvent(event, "UNKNOWN")
   }
 }

 object SerialPortManager {
   private final val log = Logger.getLogger("SerialPortManager")
   private final val ENCODING = StandardCharsets.US_ASCII

   @throws(classOf[NumberFormatException])
   @throws(classOf[ArrayIndexOutOfBoundsException])
   def selectPortConsole : String = {
     val portNames = SerialPortList.getPortNames
     println("Please select the Serial Port ID of an OpenBCI:")
     ((0 until portNames.length) zip portNames).foreach(com =>
       println("ID " + com._1.toString + ":\t" + com._2))
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

 /** Class implementing the JSSC serialEvent method.
  *  Drawn from java-simple-serial-connector/wiki/jSSC_examples
  */
 @throws(classOf[SerialPortException])
 class SerialPortManager(private val portName: String, private val packetSize: Int = 33) {

   /** Write a character to the serial port as a byte.
    *  OpenBCI documentation recommends a delay after each write.
    *  Any delay less than 24ms appears to leave the buffer empty.
    *  24ms appears to occasionally cause no data to be trasferred.
    *  @param c A character
    *  @return Nothing
    */
   @throws(classOf[SerialPortException])
   def write(c: Char) : Char = {
     serialPort.writeByte(c.toByte)
     Thread.sleep(50, 0)
     SerialPortManager.log.info("Wrote ASCII protocol character: " + c)
     c
   }

   /** Write a string to the serial port as a byte array.
    *  OpenBCI documentation reccomends a delay after each write.
    *  @param s A string
    *  @return Nothing
    */
   def write(s: String) : String = { s.map(write); s }

   /** Write an integer to the serial port as a byte array.
    *  TODO: Convert integer to a protocol character (e.g. QWERTY...)
    *  @param i An integer
    *  @return Nothing
    */
   def write(i: Int) : Int = { (i + '0').toString.map(write); i }

   /** Write a boolean to the serial port as a byte.
    *  @param b A boolean
    *  @return Nothing
    */
   private def booleanToChar(b: Boolean) = if(b) '1' else '0'

   def write(b: Boolean) : Boolean = { write(booleanToChar(b)); b }

   /** Write a short to the serial port as a byte array.
    *  @param s A short
    *  @return Nothing
    */
   def write(s: Short) : Short = { write(s.toInt); s }

   /** Close the connection to the serial port and release
    *  all serial port handlers.
    *  @return Whether or not the serial port closed successfully
    */
   @throws(classOf[SerialPortException])
   def close { 
     SerialPortManager.log.info("Closing serial port: " + serialPort.getPortName)
     if(!serialPort.isOpened)
       throw new SerialPortException(portName,
         "SerialPortManager.close",
         SerialPortException.TYPE_PORT_NOT_OPENED)
     serialPort.purgePort(SerialPort.PURGE_RXCLEAR|SerialPort.PURGE_TXCLEAR)
     try    { serialPort.closePort }
     catch  { case e: SerialPortException => throw e }
   }

   /** Check if the managed serial port is open.
    *  @return True if the serial port is open.
    */
   def isOpen: Boolean = serialPort.isOpened

   /** Get the name of the managed serial port.
    *  @return The name of the serial port
    */
   def getPortName: String = serialPort.getPortName

  def readUntil(eom: String): String = {
    var message: String  = ""
    while(!message.contains(eom)) {
      val buf: Array[Byte] = serialPort.readBytes
      if(buf != null)
        message += new String(buf)
    }
    SerialPortManager.log.info("Response: " + message)
    message
  }

  def readBinaryData {
    while(!Thread.currentThread.isInterrupted)
      println(OpenBCIPacketV3(serialPort.readBytes(packetSize)).toString)
  }

  /* SerialPort may throw SerialPortException.TYPE_PORT_NOT_FOUND  */
  private val serialPort = new SerialPort(portName)

  if(serialPort.isOpened)
    throw new SerialPortException(portName, "SerialPortManager",
      SerialPortException.TYPE_PORT_ALREADY_OPENED)

  try   { serialPort.openPort }
  catch { case e: SerialPortException => close; throw e }

  if(!serialPort.setParams(RFDuinoUSBDongle.baud, RFDuinoUSBDongle.dataBits,
    RFDuinoUSBDongle.stopBits, RFDuinoUSBDongle.parity)) {
      close
      throw new SerialPortException(portName, "SerialPortManager",
        SerialPortException.TYPE_PARAMETER_IS_NOT_CORRECT)
    }

  serialPort.purgePort(SerialPort.PURGE_RXCLEAR|SerialPort.PURGE_TXCLEAR)

  def listenBinary {
    serialPort.setEventsMask(SerialPort.MASK_RXCHAR + SerialPort.MASK_CTS + SerialPort.MASK_DSR)
    serialPort.addEventListener(SerialPortReader(serialPort))
  }

  // Pausing 2000ms and then readString causes the buffer to fill up fully until $$$,
  // and no further data is sent.
  //
  // By calling readString in a loop, parts of the string are returned, but not including the $$$.
  // It appears it will be necessary to provide a read-loop for any and all commands that are expected
  // to yield a response.

  // serialPort.closePort()

 }
}
