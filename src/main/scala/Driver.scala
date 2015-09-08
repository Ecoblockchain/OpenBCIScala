import java.io.{File, PrintWriter}
import java.lang.{
  IllegalArgumentException,
  UnsatisfiedLinkError
}
import java.time.LocalDateTime
import java.util.concurrent.LinkedBlockingQueue
import jssc.SerialPortException
import org.openbci.dongle.RFDuinoUSB
import org.openbci.serial.SerialPortManager
import org.openbci.packets.PacketParserTask
import scala.util.{ Try, Success, Failure }

object OpenBCIDriver {
  def main(argv: Array[String]) {

    /* Set up logging directory for EEG data packets.
     */
    val logDirectory = new File("logs")
    logDirectory.mkdir
    val logFile = logDirectory.toString + System.getProperty("file.separator") + "OpenBCI-Data-Packets_" + LocalDateTime.now.toString.replaceAll(":","-") +".txt"
    val logWriter: PrintWriter = new PrintWriter(logFile, "US-ASCII")

    /* Get the name of the serial port on which the OpenBCI USB Dongle resides.
     */
    val portName: Try[String] =
      Try(SerialPortManager.selectPortConsole) match {
      case Success(s) => Success(s)
      case Failure(e) =>
        println(e.getMessage)
        System.exit(1)
        Failure(e)
    }

    /* Create the object which will manage writes to the serial port.
     */
    val serPortMgr: Try[SerialPortManager] =
      Try(new SerialPortManager(portName.get)) recover {
        case e: UnsatisfiedLinkError =>
        println("OpenBCI driver encountered a JSSC link error:")
        e.printStackTrace
        println("Ensure forking is enabled in build.sbt.")
        null

        case e: SerialPortException => 
        print("OpenBCI driver encountered a JSSC exception on port ")
        println(e.getPortName + ": " + e.getExceptionType)
        null

        case e: Throwable =>
        println("OpenBCI driver encountered an exception:")
        e.printStackTrace
        null
      }

    /* Abstraction over the serial port for OpenBCI-specific protocol actions.
     */
    val openBCIConn: RFDuinoUSB = serPortMgr match {
      case Success(s) => RFDuinoUSB(s, true, 8)
      case Failure(_) => System.exit(1); null
    }

    /* Begin reading into a queue shared with a packet-parsing thread.
     */
    val eventQueue = new LinkedBlockingQueue[Array[Byte]]
    openBCIConn.init(eventQueue)

    /* The packet-parsing thread will log all sucessfully transmuted packets.
     */
    val parserThread = new Thread(new PacketParserTask(eventQueue, logWriter))
    parserThread.start

    Thread.sleep(10000, 0)

    parserThread.interrupt
    openBCIConn.close
    logWriter.close

    println("EEG Data was logged to the file: " + logFile)
  }
}
