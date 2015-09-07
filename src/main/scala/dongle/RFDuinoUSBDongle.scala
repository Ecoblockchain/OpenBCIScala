import java.lang.IllegalArgumentException
import java.util.concurrent.LinkedBlockingQueue
import jssc.{SerialPort, SerialPortException}
import org.openbci.ADS1299
import org.openbci.serial.SerialPortManager

package org.openbci.dongle {
  object RFDuinoUSB {
    final val baud                   = SerialPort.BAUDRATE_115200
    final val dataBits               = SerialPort.DATABITS_8
    final val stopBits               = SerialPort.STOPBITS_1
    final val parity                 = SerialPort.PARITY_NONE

    private final val EOT            = "$$$"
    private final val unusedCommands = " ~`9()_{}oOfghkl;:'\"VnNM,<.>/"
    private final val comInitMSec    = 3000

    private final val commands : Map[Symbol, Char] =
      /* NB. Per http://docs.openbci.com/software/01-OpenBCI_SDK:
       * The 'v' command is only required for 32-bit OpenBCI
       * The 'C' command is only required for  8-bit OpenBCI */
      Map('queryRegisterSettings -> '?',
        'stopBinary      -> 's',
        'softReset       -> 'v',
        'startBinary     -> 'b', 'startBinaryWAux    -> 'n',
        'use8Channels    -> 'c', 'use16Channels      -> 'C',
        'activateFilters -> 'f', 'deactivateFilters  -> 'g',
        'enableCh1       -> '!', 'disableCh1         -> '1',  // Channel Management
        'enableCh2       -> '@', 'disableCh2         -> '2',
        'enableCh3       -> '#', 'disableCh3         -> '3',
        'enableCh4       -> '$', 'disableCh4         -> '4',
        'enableCh5       -> '%', 'disableCh5         -> '5',
        'enableCh6       -> '^', 'disableCh6         -> '6',
        'enableCh7       -> '&', 'disableCh7         -> '7',
        'enableCh8       -> '*', 'disableCh8         -> '8',
        'enableCh9       -> 'Q', 'disableCh9         -> 'q',
        'enableCh10      -> 'W', 'disableCh10        -> 'w',
        'enableCh11      -> 'E', 'disableCh11        -> 'e',
        'enableCh12      -> 'R', 'disableCh12        -> 'r',
        'enableCh13      -> 'T', 'disableCh13        -> 't',
        'enableCh14      -> 'Y', 'disableCh14        -> 'y',
        'enableCh15      -> 'U', 'disableCh15        -> 'u',
        'enableCh16      -> 'I', 'disableCh16        -> 'i',
        'setChannelSettingsDefault  -> 'd', 'getChannelSettingsDefault  -> 'D',
        // Modal commands that require additional parameters.
        'enterChannelSettings       -> 'x', 'latchChannelSettings       -> 'X',
        'enterImpedanceSettings     -> 'z', 'latchImpedanceSettings     -> 'Z',
        // SD Card Logging
        'sdCardLog5Mins   -> 'A', 'sdCardLog15Mins    -> 'S', 'sdCardLog30Mins  -> 'F',
        'sdCardLog1Hour   -> 'G', 'sdCardLog2Hours    -> 'H', 'sdCardLog4Hours  -> 'J',
        'sdCardLog12Hours -> 'K', 'sdCardLog24Hours   -> 'L',
        'sdCardLogTest    -> 'a', 'sdCardCloseFile    -> 'j',
        // Calibration and internal test signals
        'measureNoise     -> '0', // Connect all inputs to internal GND
        'measureDC        -> 'p', // Connect all inputs to DC
        'testSlowPulse1x  -> '-',
        'testFastPulse1x  -> '=',
        'testSlowPulse2x  -> '[',
        'testFastPulse2x  -> ']')

    def apply(spm: SerialPortManager, is32Bit: Boolean = false, chan: Int = 8) =
      new RFDuinoUSB(spm, is32Bit, chan)
  }

  /** Class for sending data to and receiving data from an OpenBCI board.
   * 
   * Constructor is drawn partially from the syncWithHardware()
   * routine from the OpenBCI_Processing project.
   *
   * @constructor Creates a new OpenBCI managed by a SerialPortManager
   * @param spm A SerialPortManager that communicates with the OpenBCI.
   * @param channels The number of channels available (8 or 16).
   */
  class RFDuinoUSB(spm: SerialPortManager, is32Bit: Boolean = false, chan: Int = 8) {

    private var opened   = false
    private val channels = if(chan > 8) 16 else 8

    /** Write a protocol charecter via the serial port manager.
     *  @param c A character
     *  @return Nothing
     */
    @throws(classOf[IllegalArgumentException])
    @throws(classOf[SerialPortException])
    private def write(c: Char) =
      if(RFDuinoUSB.unusedCommands.contains(c))
        throw new IllegalArgumentException(c + " is not an OpenBCI protocol character.")
      else if(!spm.isOpen)
        throw new SerialPortException(spm.getPortName,
          "RFDuinoUSB.write", SerialPortException.TYPE_PORT_NOT_OPENED)
      else
        spm.write(c)

      /** Write any valid OpenBCI ASCII protocol command to the RFDuino dongle.
       *
       *  @param c A command character
       *  @return Nothing
       */
      @throws(classOf[IllegalArgumentException])
      def writeCommand(cmd: Symbol) {
        val command = RFDuinoUSB.commands get cmd
        command match {
          case Some(c) => spm.write(c)
          case None => throw new IllegalArgumentException(cmd + " is not an OpenBCI protocol command.")
        }
      }

      /** Write settings for a channel to the OpenBCI.
       *
       * @param channel The channel number in [0, 15]
       * @param powerDown Whether or not to power down the channel
       * @param gain The channel gain
       * @param inputType
       * @param biasInclusion Whether or not to include the channel in bias calculation
       * @param connectPtoSRB2
       * @param connectPtoSRB1
       * @return Nothing
       */
      def setChannelSettings(channel: Short,
        powerDown: Boolean      = false,
        gain: Int               = 24,
        inputType: Symbol       = 'ADSINPUT_NORMAL,
        biasInclusion: Boolean  = true,
        connectPtoSRB2: Boolean = true,
        connectPtoSRB1: Boolean = false) {
          writeCommand('enterChannelSettings)
          try {
            spm.write(channel)
            spm.write(powerDown)
            spm.write(gain)
            spm.write(ADS1299.adcInputTypes getOrElse(inputType, 0))
            spm.write(biasInclusion)
            spm.write(connectPtoSRB2)
            spm.write(connectPtoSRB1)
          } catch {
            case e: IllegalArgumentException =>
            // If any write failed, reset to channel to defaults
            writeCommand('latchChannelSettings)
            writeCommand('enterChannelSettings)
            spm.write(channel)
            spm.write(false)
            spm.write(24)
            spm.write(ADS1299.adcInputTypes('ADSINPUT_NORMAL))
            spm.write(true)
            spm.write(true)
            spm.write(false)
            throw e
            } finally {
              writeCommand('latchChannelSettings)
            }
      }

      /** Read characters until the EOM string is found
       * @return A prefix of the bytes read without the EOM string.
       */
      def writeCommandAndWait(s: Symbol) = { writeCommand(s); spm.readUntil("$$$") }

      /** Shut down the connection to this OpenBCI
       *  @return Nothing
       */
      @throws(classOf[SerialPortException])
      def close {
        writeCommand('stopBinary)
        writeCommand('sdCardCloseFile)
        try     { spm.close }
        catch   { case e: SerialPortException => throw e }
        finally { opened = false }
      }

      /** Open a connection to this OpenBCI
       *  @return Nothing
       */
      def init(events: LinkedBlockingQueue[Array[Byte]], useAux: Boolean = false): Unit =
        if(!opened) {
          if(is32Bit)
            writeCommandAndWait('softReset)
          if(8 == channels)
            writeCommand('use8Channels)
          else
            writeCommand('use16Channels)
          writeCommand('setChannelSettingsDefault)
          writeCommandAndWait('getChannelSettingsDefault)
          writeCommandAndWait('queryRegisterSettings)
          writeCommand('sdCardCloseFile)
          // NB. startBinaryWAux appears to do nothing on a V3 OpenBCI 32-Bit.
          writeCommand(if(useAux) 'startBinaryWAux else 'startBinary)
          spm.listen(events)
          opened = true
        }

      /* What's this about?
      if (prefered_datamode == DATAMODE_BIN_WAUX) {
        if (!useAux) { //must be requesting the aux data, so change the referred data mode
          prefered_datamode = DATAMODE_BIN;
          nAuxValues = 0;
          //println("OpenBCI_ADS1299: nAuxValuesPerPacket =
          //" + nAuxValuesPerPacket +
          //" so setting prefered_datamode to "
          //+ prefered_datamode);
        }
      }
      */
  }
}
