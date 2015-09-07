/* Imports from the RXTX Serial Communications library. */
import java.util.logging.Logger
import java.lang.IllegalArgumentException
import jssc.{SerialPort, SerialPortException}
import org.OpenBCI.SerialPort.SerialPortManager

package org.OpenBCI {
  object OpenBCIPacket {

    @throws(classOf[IllegalArgumentException])
    def Int24BEToInt32Native(bytes: Array[Byte]): Int = {
      if(bytes.length == 3)
        (0xFF & bytes(0) << 16) | (0xFF & bytes(1) << 8) | (0xFF & bytes(2)) match {
          case neg if 0 < (neg & 0x00800000) => neg | 0xFF000000 
          case pos => pos & 0x00FFFFFF
        }
      else
        throw new IllegalArgumentException("An Int24 packet element must have a length of 3 bytes.")
    }

    @throws(classOf[IllegalArgumentException])
    def Int16BEToInt32Native(bytes: Array[Byte]): Int = {
      if(bytes.length == 2)
        (0xFF & bytes(0) << 8) | (0xFF & bytes(1)) match {
          case neg if 0 < (neg & 0x00008000) => neg | 0xFFFF0000 
          case pos => pos & 0x0000FFFF
        }
      else
        throw new IllegalArgumentException("An Int16 packet element must have a length of 2 bytes.")
    }
  }

  object OpenBCIPacketV3 {
    def apply(raw: Array[Byte]) = new OpenBCIPacketV3(raw)
  }

  class OpenBCIPacketV3(private val raw: Array[Byte]) {
    if(raw.size != 33)
      throw new IllegalArgumentException("An OpenBCI V3 Packet consists of 33 bytes.")
    val header: Byte     = raw(0)
    val sample: Int      = raw(1) & 0xFF
    val eegChannel1: Int = OpenBCIPacket.Int24BEToInt32Native(raw.slice(2,5))
    val eegChannel2: Int = OpenBCIPacket.Int24BEToInt32Native(raw.slice(5,8))
    val eegChannel3: Int = OpenBCIPacket.Int24BEToInt32Native(raw.slice(8,11))
    val eegChannel4: Int = OpenBCIPacket.Int24BEToInt32Native(raw.slice(11,14))
    val eegChannel5: Int = OpenBCIPacket.Int24BEToInt32Native(raw.slice(14,17))
    val eegChannel6: Int = OpenBCIPacket.Int24BEToInt32Native(raw.slice(17,20))
    val eegChannel7: Int = OpenBCIPacket.Int24BEToInt32Native(raw.slice(20,23))
    val eegChannel8: Int = OpenBCIPacket.Int24BEToInt32Native(raw.slice(23,26))

    val accelX: Int      = OpenBCIPacket.Int16BEToInt32Native(raw.slice(26,28))
    val accelY: Int      = OpenBCIPacket.Int16BEToInt32Native(raw.slice(28,30))
    val accelZ: Int      = OpenBCIPacket.Int16BEToInt32Native(raw.slice(30,32))
    val footer: Byte     = raw(32)

    if(header != 0xA0.toByte || footer != 0xC0.toByte)
       throw new IllegalArgumentException("Header (" + header + ") or footer (" + footer + ") was corrupted.")

    override def equals(e: Any): Boolean =
      e match {
        case p: OpenBCIPacketV3 => raw.equals(p.raw)
        case _ => false
      }

    override def toString: String =
      "[" ++ (List(header.toString, sample,
        eegChannel1, eegChannel2, eegChannel3, eegChannel4,
        eegChannel5, eegChannel6, eegChannel7, eegChannel8,
        accelX, accelY, accelZ,
        footer.toString) mkString ", ") ++ "]"
  }

  object RFDuinoUSBDongle {
    final val baud        = jssc.SerialPort.BAUDRATE_115200
    final val dataBits    = jssc.SerialPort.DATABITS_8
    final val stopBits    = jssc.SerialPort.STOPBITS_1
    final val parity      = jssc.SerialPort.PARITY_NONE

  }

  /** Singleton containing static constants used by the OpenBCI Analog Front End
   *
   */
  object ADS1299 {
    // sample rate used by OpenBCI board...set by its Arduino code
    final val fs_Hz        : Float = 250.0f 

    // reference voltage for ADC in ADS1299.  set by its hardware
    final val ADS1299_Vref : Float = 4.5f

    // assumed gain setting for ADS1299.  set by its Arduino code
    final val ADS1299_gain : Float = 24.0f 

    // ADS1299 datasheet Table 7, confirmed through experiment
    final val scale_fac_uVolts_per_count : Float = (ADS1299_Vref / (math.pow(2,23)-1) / ADS1299_gain * 1000000).toFloat

    // assume set to +/4G, so 2 mG per digit (datasheet). Account for 4 bits unused
    final val scale_fac_accel_G_per_count : Float = (0.002 / math.pow(2,4)).toFloat

    // 6 nA, set by its Arduino code
    final val leadOffDrive_amps : Float = 6.0e-9f

    // float LIS3DH_full_scale_G = 4; 
    //  +/- 4G, assumed full scale setting for the accelerometer
    // final float scale_fac_accel_G_per_count = 1.0;

    private final val dataModes : Map[Symbol, Int] =
      Map('NONE     -> -1,
        'TXT        ->  0,
        'BIN_WAUX   ->  1, /* Indicates that accelerometer data is included. */
       'BIN        ->  2,
       'BIN_4CHAN  ->  4)

    private final val states : Map[Symbol, Int] =
      Map('NOCOM          -> 0,
        'COMINIT          -> 1,
        'SYNCWITHHARDWARE -> 2,
        'NORMAL           -> 3,
        'STOPPED          -> 4)

    private final val bytes : Map[Symbol, Byte] =
      Map('START -> 0xA0.toByte,
        'END -> 0xC0.toByte)

    private final val EOT = "$$$"
    private final val unusedCommands = " ~`9()_{}oOfghkl;:'\"VnNM,<.>/"
    private final val comInitMSec = 3000

    // Gain defaults to 24 dB.
    // Keys are dB; values are arguments to 'x'
    private final val gainLevels : Map[Int, Int] =
      Map(0 -> 0,
        2   -> 1,
        4   -> 2,
        6   -> 3,
        8   -> 4,
        12  -> 5,
        24  -> 6)

    private final val adcInputTypes : Map[Symbol, Int] =
      Map( // NB. ADSINPUT_NORMAL by default
        'ADSINPUT_NORMAL	-> 0,
        'ADSINPUT_SHORTED	-> 1,
        'ADSINPUT_BIAS_MEAS	-> 2,
        'ADSINPUT_MVDD	-> 3,
        'ADSINPUT_TEMP	-> 4,
        'ADSINPUT_TESTSIG	-> 5,
        'ADSINPUT_BIAS_DRP	-> 6,
        'ADSINPUT_BIAS_DRN	-> 7)

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
        new ADS1299(spm, is32Bit, chan)
  }

  /** Class for sending data to and receiving data from an OpenBCI board.
   * 
   * @constructor Creates a new OpenBCI managed by a SerialPortManager
   * @param spm A SerialPortManager that communicates with the OpenBCI.
   * @param channels The number of channels available (8 or 16).
   */
  class ADS1299(spm: SerialPortManager, is32Bit: Boolean = false, chan: Int = 8) {

    private val channels = if(chan > 8) 16 else 8
    private var state    = ADS1299.states get 'NOCOM
    private var dataMode = ADS1299.dataModes get 'NONE
    private var preferredDataMode = ADS1299.dataModes get 'BIN_WAUX

    /** Write a protocol charecter via the serial port manager.
     *  @param c A character
     *  @return Nothing
     */
    @throws(classOf[IllegalArgumentException])
    private def write(c: Char) =
      if(ADS1299.unusedCommands.contains(c))
        throw new IllegalArgumentException(c + " is not an OpenBCI protocol character.")
      else if(!spm.isOpen)
        throw new SerialPortException(spm.getPortName,
          "ADS1299.write", SerialPortException.TYPE_PORT_NOT_OPENED)
      else
        spm.write(c)

      /** Write any valid OpenBCI ASCII protocol command to the RFDuino dongle.
       *
       *  @param c A command character
       *  @return Nothing
       */
      @throws(classOf[IllegalArgumentException])
      def writeCommand(cmd: Symbol) {
        val command = ADS1299.commands get cmd
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
      def close {
        writeCommand('stopBinary)
        writeCommand('sdCardCloseFile)
        spm.close
      }

      /** Open a connection to this OpenBCI
       *  @return Nothing
       */
      def open(useAux: Boolean = false) {
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
        /* NB. startBinaryWAux appears to do nothing on a V3 OpenBCI 32-Bit. */
        writeCommand(if(useAux) 'startBinaryWAux else 'startBinary)
        spm.listenBinary
      }

      /* Constructor is drawn primarily from the syncWithHardware
       * routine from the OpenBCI_Processing project. */

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
