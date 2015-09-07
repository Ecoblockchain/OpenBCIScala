import java.lang.{
  NumberFormatException,
  ArrayIndexOutOfBoundsException,
  IllegalArgumentException,
  UnsatisfiedLinkError
}
import jssc.SerialPortException
import org.OpenBCI.ADS1299
import org.OpenBCI.SerialPort._
import scala.util.{ Try, Success, Failure }

object OpenBCIDriver {
  def main(argv: Array[String]) {
    val portName: Try[String] =
      Try(SerialPortManager.selectPortConsole) match {
      case Success(s) => Success(s)
      case Failure(e) =>
        println(e.getMessage)
        System.exit(1)
        Failure(e)
    }

    val serPortMgr: Try[SerialPortManager] =
      Try(new SerialPortManager(portName.get)) recover {
        case e: UnsatisfiedLinkError =>
        println("OpenBCI driver encountered a JSSC link error:")
        e.printStackTrace
        println("Ensure forking is enabled in build.sbt.")
        null

        case e: IllegalArgumentException =>
        println(e.getMessage)
        null

        case e: SerialPortException => 
        print("OpenBCI driver encountered a JSSC exception on port ")
        println(e.getPortName + ": " + e.getExceptionType)
        null

        case e: Throwable =>
        println("OpenBCI driver encountered an unhandled exception:")
        e.printStackTrace
        null
      }

    val ads1299: ADS1299 = serPortMgr match {
      case Success(s) => ADS1299(s, true, 8)
      case Failure(_) => System.exit(1); null
    }

    ads1299.open()
    Thread.sleep(10000, 0)
    ads1299.close
  }
}
