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

class ADS1299 {
  private final val float fs_Hz         : Float = 250.0f  //sample rate used by OpenBCI board...set by its Arduino code
  private final val float ADS1299_Vref  : Float = 4.5f    //reference voltage for ADC in ADS1299.  set by its hardware
  private final val ADS1299_gain        : Float = 24.0f   //assumed gain setting for ADS1299.  set by its Arduino code
  private final val scale_fac_uVolts_per_count  : Float = ADS1299_Vref / ((float)(pow(2,23)-1)) / ADS1299_gain  * 1000000.f //ADS1299 datasheet Table 7, confirmed through experiment
  private final val scale_fac_accel_G_per_count : Float = 0.002 / ((float)pow(2,4))  //assume set to +/4G, so 2 mG per digit (datasheet). Account for 4 bits unused
  private final val leadOffDrive_amps : Float = 6.0e-9  //6 nA, set by its Arduino code
  //float LIS3DH_full_scale_G = 4;  // +/- 4G, assumed full scale setting for the accelerometer
  //final float scale_fac_accel_G_per_count = 1.0;

}

class OpenBCIState {
  private final val dataModes : Map[Symbol, Int] =
    Map('NONE     -> -1,
      'TXT        -> 0,
      'BIN_WAUX   -> 1,
      'BIN        -> 2,
      'BIN_4CHAN  -> 4)

  private final val states : Map[Symbol, Int] =
    Map('NOCOM          -> 0,
      'COMINIT          -> 1,
      'SYNCWITHHARDWARE -> 2,
      'NORMAL           -> 3,
      'STOPPED          -> 4)

  private final val Bytes : Map[Symbol, Byte] =
    Map('START -> 0xA0.toByte, 'END -> 0xC0.toByte)

  private final val EOT = "$$$"

  private var state    = states get 'NOCOM
  private var dataMode = dataModes get 'NONE

  private final val comInitMSec = 3000

}

class OpenBCIADS1299(comPort: String, baud: Int, aux: Bool) {
  private final val commands : Map[Symbol, Char] =
    Map('stop            -> 's',
      'startBinary       -> 'b', 'startBinaryWAux   -> 'n', 'startBinary4Chan  -> 'v',
      'activateFilters   -> 'f', 'deactivateFilters -> 'g',
      'EnableCh1         -> '1', 'DisableCh1        -> '!',
      'EnableCh2         -> '2', 'DisableCh2        -> '@',
      'EnableCh3         -> '3', 'DisableCh3        -> '#',
      'EnableCh4         -> '4', 'DisableCh4        -> '$',
      'EnableCh5         -> '5', 'DisableCh5        -> '%',
      'EnableCh6         -> '6', 'DisableCh6        -> '^',
      'EnableCh7         -> '7', 'DisableCh7        -> '&',
      'EnableCh8         -> '8', 'DisableCh8        -> '*',
      'EnableCh9         -> 'q', 'DisableCh9        -> 'Q',
      'EnableCh10        -> 'w', 'DisableCh10       -> 'W',
      'EnableCh11        -> 'e', 'DisableCh11       -> 'E',
      'EnableCh12        -> 'r', 'DisableCh12       -> 'R',
      'EnableCh13        -> 't', 'DisableCh13       -> 'T',
      'EnableCh14        -> 'y', 'DisableCh14       -> 'Y',
      'EnableCh15        -> 'u', 'DisableCh15       -> 'U',
      'EnableCh16        -> 'i', 'DisableCh16       -> 'I')
}
