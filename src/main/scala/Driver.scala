import jssc.SerialPortException
import org.OpenBCI.SerialPort._
import scala.util.{
  Try,
  Success,
  Failure
}
import java.lang.{
  NumberFormatException,
  ArrayIndexOutOfBoundsException,
  IllegalArgumentException,
  UnsatisfiedLinkError
}

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
        println("(NB. SBT is probably fried. Try restarting SBT.)")
        System.exit(3)
        return

        case e: IllegalArgumentException =>
        println(e.getMessage)
        System.exit(4)
        return

        case e: SerialPortException => 
        print("OpenBCI driver encountered a JSSC exception on port ")
        println(e.getPortName + ": " + e.getExceptionType)
        System.exit(5)
        return

        case e: Throwable =>
        println("OpenBCI driver encountered an unhandled exception:")
        e.printStackTrace
        System.exit(6)
        return
      }
  }
}
