package org.OpenBCI.SerialPort {
  import scala.io.StdIn
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
   private val portNames = SerialPortList.getPortNames

   def close() : Boolean = 
     try { 
       serialPort.closePort // TODO: Log this Boolean value.
     } catch {
       case e: SerialPortException => throw e
     }

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
