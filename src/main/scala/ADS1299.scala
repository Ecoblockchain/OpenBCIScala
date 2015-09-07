package org.openbci {
  object ADS1299 {
    // Gain defaults to 24 dB.
    // Keys are dB; values are arguments to 'x'
    final val gainLevels : Map[Int, Int] =
      Map(0 -> 0,
        2   -> 1,
        4   -> 2,
        6   -> 3,
        8   -> 4,
        12  -> 5,
        24  -> 6)

    final val adcInputTypes : Map[Symbol, Int] =
      Map( // NB. ADSINPUT_NORMAL by default
        'ADSINPUT_NORMAL	-> 0,
        'ADSINPUT_SHORTED	-> 1,
        'ADSINPUT_BIAS_MEAS	-> 2,
        'ADSINPUT_MVDD	        -> 3,
        'ADSINPUT_TEMP	        -> 4,
        'ADSINPUT_TESTSIG	-> 5,
        'ADSINPUT_BIAS_DRP	-> 6,
        'ADSINPUT_BIAS_DRN	-> 7)

    // sample rate used by OpenBCI board...set by its Arduino code
    final val fs_Hz        : Float = 250.0f 

    // reference voltage for ADC in RFDuinoUSBDongle.  set by its hardware
    final val ADS1299_Vref : Float = 4.5f

    // assumed gain setting for RFDuinoUSBDongle.  set by its Arduino code
    final val ADS1299_gain : Float = 24.0f 

    // ADS1299 datasheet Table 7, confirmed through experiment
    final val scale_fac_uVolts_per_count : Float = (ADS1299_Vref / (math.pow(2,23)-1) / ADS1299_gain * 1000000).toFloat

    // assume set to +/4G, so 2 mG per digit (datasheet). Account for 4 bits unused
    final val scale_fac_accel_G_per_count : Float = (0.002 / math.pow(2,4)).toFloat

    // 6 nA, set by its Arduino code
    final val leadOffDrive_amps : Float = 6.0e-9f

    //  +/- 4G, assumed full scale setting for the accelerometer
    final val LIS3DH_full_scale_G: Float = 4f;
  }
}
