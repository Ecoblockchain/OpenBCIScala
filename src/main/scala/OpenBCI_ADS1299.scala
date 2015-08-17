/* Imports from the RXTX Serial Communications library. */
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import org.OpenBCI.SerialPort

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
  
  private final val adcInputTypes = Map[Symbol, Int] =
    Map( // NB. ADSINPUT_NORMAL by default
    'ADSINPUT_NORMAL	-> 0 
    'ADSINPUT_SHORTED	-> 1 
    'ADSINPUT_BIAS_MEAS	-> 2 
    'ADSINPUT_MVDD	-> 3 
    'ADSINPUT_TEMP	-> 4 
    'ADSINPUT_TESTSIG	-> 5 
    'ADSINPUT_BIAS_DRP	-> 6 
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
      'sdCardLogTest    -> 'a', 'sdCardStopLogging  -> 'j',
      // Calibration and internal test signals
      'measureNoise     -> '0', // Connect all inputs to internal GND
      'measureDC        -> 'p', // Connect all inputs to DC
      'testSlowPulse1x  -> '-',
      'testFastPulse1x  -> '=',
      'testSlowPulse2x  -> '[',
      'testFastPulse2x  -> ']')
}

class OpenBCIADS1299(spm: SerialPortManager) {
  private var state    = OpenBCIADS1299.states get 'NOCOM
  private var dataMode = OpenBCIADS1299.dataModes get 'NONE
  private var preferredDataMode = OpenBCIADS1299.dataModes get 'BIN_WAUX

  def writeCommandCharacter(cmd: Char) =
    if(commands.contains(cmd))
      spm.write(cmd.toBytes)
    else
      ()

  def setChannelSettings(channel: Short,
    powerDown: Boolean = false,
    gain:  Int = 24,
    inputType: Short = OpenBCIADS1288.adcInputTypes get 'ADSINPUT_NORMAL,
    biasInclusion: Boolean = true,
    connectPtoSRB2: Boolean = true,
    connectPtoSRB1: Boolean = false) {
      // Do input checking
      writeCommandCharacter(commands get 'enterChannelSettings)
      writeCharacter(powerDown)
      writeCharacter(gain)
      writeCharacter(inputType)
      writeCharacter(biasInclusion)
      writeCharacter(connectPinSRB2)
      writeCharacter(connectPinSRB1)
      writeCommandCharacter(commands get 'latchChannelSettings)
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
