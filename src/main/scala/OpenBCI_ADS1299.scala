/* Imports from the RXTX Serial Communications library. */
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import org.OpenBCI.SerialPort

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
      'TXT        -> 0,
      'BIN_WAUX   -> 1, /* Indicates that accelerometer data is included. */
      'BIN        -> 2,
      'BIN_4CHAN  -> 4)

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
      'stop            -> 's',
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
      'setDefaultChannelSettings  -> 'd', 'getDefaultChannelSettings  -> 'D',
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
}

/** Class for sending data to and receiving data from an OpenBCI board.
 * 
 * @constructor Creates a new OpenBCI managed by a SerialPortManager
 * @param spm A SerialPortManager that communicates with the OpenBCI.
 * @param channels The number of channels available (8 or 16).
 */
class ADS1299(spm: SerialPort.SerialPortManager,
  is32Bit:  Boolean = false,
  chan:     Int     = 8) {
  private val channels = if(chan > 8) 16 else 8
  private var state    = ADS1299.states get 'NOCOM
  private var dataMode = ADS1299.dataModes get 'NONE
  private var preferredDataMode = ADS1299.dataModes get 'BIN_WAUX

  private def booleanToChar(b: Boolean) = if(b) '1' else '0'

  /** Write a character to the RFDuino dongle.
   *  @param c A character
   *  @return Nothing
   */
  @throws(classOf[IllegalArgumentException])
  private def writeChar(c: Char) =
    if(ADS1299.unusedCommands.contains(c))
      throw new IllegalArgumentException(c + " is not an OpenBCI protocol character.")
    else
      spm.write(c)

  /** Write an integer to the RFDuino dongle
   *  TODO: Convert integer to a protocol character (e.g. QWERTY...)
   *  @param i An integer
   *  @return Nothing
   */
  @throws(classOf[IllegalArgumentException])
  private def writeInt(i: Int) = 
    if(1 == i.toString.length)
      writeChar((i + '0').toChar)
    else
      throw new IllegalArgumentException(i + " has more than one digit.")

  /** Write a boolean to the RFDuino
   *  @param b A boolean
   *  @return Nothing
   */
  private def writeBoolean(b: Boolean) = writeChar(booleanToChar(b))

  /** Write a short to the RFDuino
   *  @param s A short
   *  @return Nothing
   */
  private def writeShort(s: Short) = writeInt(s.toInt)

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
      case None => 
      throw new IllegalArgumentException(cmd +
        " is not an OpenBCI protocol command.")
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
        writeShort(channel)
        writeBoolean(powerDown)
        writeInt(gain)
        writeInt(ADS1299.adcInputTypes getOrElse(inputType, 0))
        writeBoolean(biasInclusion)
        writeBoolean(connectPtoSRB2)
        writeBoolean(connectPtoSRB1)
      } catch {
        case e: IllegalArgumentException =>
        // If any write failed, reset to channel to defaults
        writeCommand('latchChannelSettings)
        writeCommand('enterChannelSettings)
        writeShort(channel)
        writeBoolean(false)
        writeInt(24)
        writeInt(ADS1299.adcInputTypes('ADSINPUT_NORMAL))
        writeBoolean(true)
        writeBoolean(true)
        writeBoolean(false)
        throw e
      } finally {
        writeCommand('latchChannelSettings)
      }
  }

  /** Shut down the connection to this OpenBCI
   *  @return Nothing
   */
  def close() {
    writeCommand('sdCardCloseFile)
    spm.close
  }

  /** Open a connection to this OpenBCI
   *  @return Nothing
   */
  def open() {
    if(!spm.isOpen) {
      println("DEBUG: Port not open in SPM.")
      return
    } else if(8 == channels)
      writeCommand('use8Channels)
    else
      writeCommand('use16Channels)
    if(is32Bit)
      writeCommand('softReset)
    writeCommand('setdefaultChannelSettings)
    writeCommand('getDefaultChannelSettings)
    // read results here and wait for $$$
    writeCommand('queryRegisterSettings)
    // read results and wait for $$$
    writeCommand('sdCardCloseFile)
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
