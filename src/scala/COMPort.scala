/* Imports from the RXTX Serial Communications library. */
import gnu.io.CommPort
import gnu.io.CommPortIdentifier
import gnu.io.SerialPort
import gnu.io.NoSuchPortException
import gnu.io.PortInUseException
import gnu.io.SerialPort
import gnu.io.SerialPortEvent
import gnu.io.SerialPortEventListener

import java.io.FileDescriptor
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

import scala.collection.mutable.HashSet
import scala.collection.mutable.HashMap

class COMPort {
  def getPortTypeName(portType: Int) : String =
    portType match {
      case CommPortIdentifier.PORT_I2C      => "I2C"
      case CommPortIdentifier.PORT_PARALLEL => "PARALLEL"
      case CommPortIdentifier.PORT_RAW      => "RAW"
      case CommPortIdentifier.PORT_RS485    => "RS485"
      case CommPortIdentifier.PORT_SERIAL   => "SERIAL"
      _ => throw new NoSuchPortException
    }

  def listPorts() : HashMap[String, String] = {
    val allPorts : HashMap[String, String] = HashMap[String, String]()
    val portEnum : java.util.Enumeration[CommPortIdentifier] = CommPortIdentifier.getPortIdentifiers
    while(portEnum.hasMoreElements) {
      var com = portEnum.nextElement
      allPorts += (com.getName -> com.getPortType)
    }
    return allPorts
  }	

  def getAvailableSerialPorts() : HashSet[CommPortIdentifier] = {
    val availablePorts : HashSet[CommPortIdentifier] = HashSet[CommPortIdentifier]()
    val portEnum : java.util.Enumeration[CommPortIdentifier] = CommPortIdentifier.getPortIdentifiers
    while(portEnum.hasMoreElements) {
      var com : CommPortIdentifier = portEnum.nextElement
      com.getPortType match {
        case CommPortIdentifier.PORT_SERIAL =>
          try {
            var comPortConn = com.open("CommUtil", 50)
            availablePorts += com
          } catch {
            case e: PortInUseException => println(com.getName + " is in use.")
            case e: Exception => println(com.getName + ": unhandled exception. " + e.getStackTrace)
            case u: Throwable => println(com.getName + ": unknown error was thrown.")
          } finally {
            commPortConn.close
          }
          return comPortConn
    }
  }
}
