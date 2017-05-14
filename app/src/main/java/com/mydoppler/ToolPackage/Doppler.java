package com.mydoppler.ToolPackage;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.util.Log;

import static java.lang.Math.exp;
import static java.lang.Math.signum;

import com.mydoppler.ToolPackage.FFT.FFT;

/**
 * To find frequency, check:
 * http://stackoverflow.com/questions/18652000/record-audio-in-java-and-determine-real-time-if-a-tone-of-x-frequency-was-played
 */
public class Doppler {

    public interface OnReadCallback {
        //bandwidths are the number to the left/to the right from the pilot tone the shift was
        void onBandwidthRead(int leftBandwidth, int rightBandwidth);

        //for testing/graphing as well
        void onBinsRead(double[] bins,double[] bins2);
    }

    //base gestures. can extend to have more
    public interface OnGestureListener {
        //swipe towards
        void onPush();

        //swipe away
        void onPull();

        //self-explanatory
        void onTap();

        //self-explanatory
        void onDoubleTap();

        //self-explanatory
        void onNothing();

    }

    //prelimiary frequency stuff
    public static final float PRELIM_FREQ = 20000;
    public static final int PRELIM_FREQ_INDEX = 20000;
    public static final int MIN_FREQ = 19000;
    public static final int MAX_FREQ = 21000;


    public static final int RELEVANT_FREQ_WINDOW = 33;
    public static final int DEFAULT_SAMPLE_RATE = 44100;

    //modded from the soundwave paper. frequency bins are scanned until the amp drops below
    // 1% of the primary tone peak
    private static final double MAX_VOL_RATIO_DEFAULT = 0.1;
    private static final double SECOND_PEAK_RATIO = 0.3;
    public static double maxVolRatio = MAX_VOL_RATIO_DEFAULT;

    //for bandwidth positions in array
    private static final int LEFT_BANDWIDTH = 0;
    private static final int RIGHT_BANDWIDTH = 1;

    //I want to add smoothing
    private static final float SMOOTHING_TIME_CONSTANT = 0.5f;

    /**
     * utility variables for reading and parsing through audio data
     **/
    private AudioRecord microphone;
    private FrequencyPlayer frequencyPlayer;
    private int SAMPLE_RATE = DEFAULT_SAMPLE_RATE;

    private float frequency;
    private int freqIndex;

    private short[] buffer;
    private float[] fftRealArray;
    private float[] fftRealArray2;
    //holds the freqs of the previous iteration
    private float[] oldFreqs;
    private  float[] oldFreqs2;
    private int bufferSize = 16384;// TODO: 2016/4/27 This varible can be rised for discussion; （44100 / bufferSize）

    private Handler mHandler;
    private boolean repeat;
    FFT fft;
    FFT fft2;

    //to calibrate or not
    private boolean calibrate = true;
    Calibrator calibrator;
    /**
     * end utility variables for parsing through audio data
     **/

    //callbacks
    private boolean isGestureListenerAttached = false;
    private OnGestureListener gestureListener;
    private boolean isReadCallbackOn = false;
    private OnReadCallback readCallback;
    /**
     * variables for gesture detection
     **/
    private int previousDirection = 0;

    private int directionChanges;
    private int cyclesLeftToRead = -1;
    //wait this many before starting to read again
    private int cyclesToRefresh;
    private int directionSsame;
    private final int cyclesToRead = 5;

    private static double[] originArray;
    private static double[] originArray2;
    private static boolean firstFlag = true;

    private static double[] lastArray;
    private static double[] lastArray2;

    public Doppler() {
        //write a check to see if stereo is supported
        //bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        bufferSize = 16384;
        buffer = new short[bufferSize];

        frequency = PRELIM_FREQ;
        freqIndex = PRELIM_FREQ_INDEX;

        frequencyPlayer = new FrequencyPlayer(PRELIM_FREQ);

        microphone = new AudioRecord(MediaRecorder.AudioSource.MIC, DEFAULT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);

        mHandler = new Handler();

        calibrator = new Calibrator();
    }

    private void setFrequency(float frequency) {
        this.frequency = frequency;
        this.freqIndex = fft.freqToIndex(frequency);
    }


    public boolean start() {
        frequencyPlayer.play();
        boolean startedRecording = false;
        try {
            //you might get an error here if another app hasn't released the microphone
            microphone.startRecording();
            repeat = true;

            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    optimizeFrequency(MIN_FREQ, MAX_FREQ);
                    //assuming fft.forward was already called;
                    originArray = new double[fft.specSize()];
                    lastArray = new double[fft.specSize()];
                    originArray2 = new double[fft2.specSize()];
                    lastArray2 = new double[fft2.specSize()];
                    readMic();
                }
            });
            startedRecording = true;
        } catch (Exception e) {
            e.printStackTrace();
            Log.d("DOPPLER", "start recording error");
            return false;
        }
        if (startedRecording) {
            int bufferReadResult = microphone.read(buffer, 0, bufferSize);
            int bufferReadResul1t = getHigherP2(bufferReadResult);
            // seperate two mic information
            short[] mic1 = new short[bufferReadResult];
            short[] mic2 = new short[bufferReadResult];
            int count1 = 0, count2 = 0;
            for (int index = 0; index < bufferSize; index++) {
                if (index % 2 == 0)
                    mic1[count1++] = buffer[index];
                else
                    mic2[count2++] = buffer[index];
            }

            //get higher p2 because buffer needs to be "filled out" for FFT
            //fftRealArray = new float[getHigherP2(bufferReadResult)];
            //fft = new FFT(getHigherP2(bufferReadResult), SAMPLE_RATE);
            fftRealArray = new float[bufferReadResult / 2];
            fft = new FFT(bufferReadResult / 2, SAMPLE_RATE);
            fftRealArray2 = new float[bufferReadResult / 2];
            fft2 = new FFT(bufferReadResult / 2, SAMPLE_RATE);
        }
        return true;
    }

    public int[] getBandwidth() {
        readAndFFT();

        //rename this
        int primaryTone = freqIndex;

        double normalizedVolume = 0;
        double primaryVolume = fft.getBand(primaryTone);
        int leftBandwidth = 0;

        do {
            leftBandwidth++;
            double volume = fft.getBand(primaryTone - leftBandwidth);
            normalizedVolume = volume / primaryVolume;
        } while (normalizedVolume > maxVolRatio && leftBandwidth < RELEVANT_FREQ_WINDOW);

        //secondary bandwidths are for looking past the first minima to search for "split off" peaks, as per the paper
        int secondScanFlag = 0;
        int secondaryLeftBandwidth = leftBandwidth;

        //second scan
        do {
            secondaryLeftBandwidth++;
            double volume = fft.getBand(primaryTone - secondaryLeftBandwidth);
            normalizedVolume = volume / primaryVolume;

            if (normalizedVolume > SECOND_PEAK_RATIO) {
                secondScanFlag = 1;
            }

            if (secondScanFlag == 1 && normalizedVolume < maxVolRatio) {
                break;
            }
        } while (secondaryLeftBandwidth < RELEVANT_FREQ_WINDOW);

        if (secondScanFlag == 1) {
            leftBandwidth = secondaryLeftBandwidth;
        }

        int rightBandwidth = 0;

        do {
            rightBandwidth++;
            double volume = fft.getBand(primaryTone + rightBandwidth);
            normalizedVolume = volume / primaryVolume;
        } while (normalizedVolume > maxVolRatio && rightBandwidth < RELEVANT_FREQ_WINDOW);

        secondScanFlag = 0;
        int secondaryRightBandwidth = 0;
        do {
            secondaryRightBandwidth++;
            double volume = fft.getBand(primaryTone + secondaryRightBandwidth);
            normalizedVolume = volume / primaryVolume;

            if (normalizedVolume > SECOND_PEAK_RATIO) {
                secondScanFlag = 1;
            }

            if (secondScanFlag == 1 && normalizedVolume < maxVolRatio) {
                break;
            }
        } while (secondaryRightBandwidth < RELEVANT_FREQ_WINDOW);

        if (secondScanFlag == 1) {
            rightBandwidth = secondaryRightBandwidth;
        }

        return new int[]{leftBandwidth, rightBandwidth};

    }

    public void readMic() {
        int[] bandwidths = getBandwidth();
        int leftBandwidth = bandwidths[LEFT_BANDWIDTH];
        int rightBandwidth = bandwidths[RIGHT_BANDWIDTH];

        if (isReadCallbackOn) {
            callReadCallback(leftBandwidth, rightBandwidth);
        }

        if (isGestureListenerAttached) {
            callGestureCallback(leftBandwidth, rightBandwidth);
        }

        if (calibrate) {
            maxVolRatio = calibrator.calibrate(maxVolRatio, leftBandwidth, rightBandwidth);
        }

        if (repeat) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    readMic();
                }
            });
        }


    }


    public void setOnGestureListener(OnGestureListener listener) {
        gestureListener = listener;
        isGestureListenerAttached = true;
    }

    public void removeGestureListener() {
        gestureListener = null;
        isGestureListenerAttached = false;
    }

    public void callGestureCallback(int leftBandwidth, int rightBandwidth) {
        //early escape if need to refresh
        if (cyclesToRefresh > 0) {
            cyclesToRefresh--;
            return;
        }

        if (leftBandwidth > 4 || rightBandwidth > 4) {
            //Log.d("GESTURE CALLBACK", "Start of if statement");
            //implement gesture logic
            int difference = leftBandwidth - rightBandwidth;
            int direction = (int) signum(difference);

            //Log.d("GESTURE CALLBACK", "DIRECTION IS " + direction);
            if (direction == 1) {
                Log.d("DIRECTION", "POS");
            } else if (direction == -1) {
                Log.d("Direction", "NEG");
            } else {
                Log.d("DIrection", "none");
            }

            if (direction != 0 && direction != previousDirection) {
                //scan a 4 frame window to wait for taps or double taps
                //Log.d("GESTURE CALLBACK", "previous direction is diff than current");
                cyclesLeftToRead = cyclesToRead;
                //Log.d("GESTURE CALLBACK", "setting prev direction");
                previousDirection = direction;
                directionChanges++;
            }
        }

        cyclesLeftToRead--;

        if (cyclesLeftToRead == 0) {
            //Log.d("GESTURE CALLBACK", "No more cycles to read. finding appropriate lsitener");
            if (directionChanges == 1) {
                if (previousDirection == -1) {
                    gestureListener.onPush();
                } else {
                    gestureListener.onPull();
                }
            } else if (directionChanges == 2) {
                gestureListener.onTap();
            } else {
                gestureListener.onDoubleTap();
            }
            previousDirection = 0;
            directionChanges = 0;
            cyclesToRefresh = cyclesToRead;
        } else {
            gestureListener.onNothing();
        }
    }

    public void setOnReadCallback(OnReadCallback callback) {
        readCallback = callback;
        isReadCallbackOn = true;
    }

    public void removeReadCallback() {
        readCallback = null;
        isReadCallbackOn = false;
    }

    public void callReadCallback(int leftBandwidth, int rightBandwidth) {
        double[] array = new double[fft.specSize()];
        double[] array2=new double[fft2.specSize()];
//        double[] tmpArray = new double[fft.specSize()];
        if (firstFlag) {
            for (int i = 0; i < fft.specSize(); ++i) {
                originArray[i] = fft.getBand(i);
                array[i] = 0.0;
                lastArray[i] = 0.0;
                firstFlag = false;
            }
            for (int i = 0; i < fft2.specSize(); ++i) {
                originArray2[i] = fft2.getBand(i);
                array2[i] = 0.0;
                lastArray2[i] = 0.0;
                firstFlag = false;
            }
        } else {
            for (int i = 0; i < fft.specSize(); ++i) {
//                array[i] = 1 / (1 + exp(Math.log10(fft.getBand(i) / oldFreqs[i]))) - 0.5; // sigmoid function.
                array[i] = fft.getBand(i);
            }
            for (int i = 0; i < fft2.specSize(); ++i) {
//                array2[i] = 1 / (1 + exp(Math.log10(fft2.getBand(i) / oldFreqs2[i]))) - 0.5; // sigmoid function.
                array2[i] = fft2.getBand(i);
            }
        }

        readCallback.onBandwidthRead(leftBandwidth, rightBandwidth);
        readCallback.onBinsRead(array,array2);
    }

    public boolean setCalibrate(boolean bool) {
        calibrate = bool;
        return calibrate;
    }

    public void smoothOutFreqs() {
        for (int i = 0; i < fft.specSize(); ++i) {
            float smoothedOutMag = SMOOTHING_TIME_CONSTANT * fft.getBand(i) + (1 - SMOOTHING_TIME_CONSTANT) * oldFreqs[i];
            fft.setBand(i, smoothedOutMag);
        }
    }

    public boolean pause() {
        try {
            microphone.stop();
            frequencyPlayer.pause();
            repeat = false;
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void optimizeFrequency(int minFreq, int maxFreq) {
        readAndFFT();
        int minInd = fft.freqToIndex(minFreq);
        int maxInd = fft.freqToIndex(maxFreq);

        int primaryInd = freqIndex;
        for (int i = minInd; i <= maxInd; ++i) {
            if (fft.getBand(i) > fft.getBand(primaryInd)) {
                primaryInd = i;
            }
        }
        setFrequency(fft.indexToFreq(primaryInd));
//        Log.d("NEW PRIMARY IND", fft.indexToFreq(primaryInd) + "");
    }

    //reads the buffer into fftrealarray, applies windowing, then fft and smoothing
    public void readAndFFT() {
        //copy into old freqs array
        if (fft.specSize() != 0 && oldFreqs == null) {
            oldFreqs = new float[fft.specSize()];
        }
        for (int i = 0; i < fft.specSize(); ++i) {
            oldFreqs[i] = fft.getBand(i);
        }
        if (fft2.specSize() != 0 && oldFreqs2 == null) {
            oldFreqs2 = new float[fft2.specSize()];
        }
        for (int i = 0; i < fft2.specSize(); ++i) {
            oldFreqs2[i] = fft2.getBand(i);
        }

        int bufferReadResult = microphone.read(buffer, 0, bufferSize);
        // seperate two mic information
        short[] mic1 = new short[bufferReadResult];
        short[] mic2 = new short[bufferReadResult];
        int count1 = 0, count2 = 0;
        for (int index = 0; index < bufferSize; index++) {
            if (index % 2 == 0)
                mic1[count1++] = buffer[index];
            else
                mic2[count2++] = buffer[index];
        }
        bufferReadResult /= 2;
        for (int i = 0; i < bufferReadResult; i++) {
            fftRealArray[i] = (float) mic1[i] / Short.MAX_VALUE; //32768.0
            fftRealArray2[i] = (float) mic2[i] / Short.MAX_VALUE;
        }

        //apply windowing
        for (int i = 0; i < bufferReadResult / 2; ++i) {
            // Calculate & apply window symmetrically around center point
            // Hanning (raised cosine) window
            float winval = (float) (0.5 + 0.5 * Math.cos(Math.PI * (float) i / (float) (bufferReadResult / 2)));
            if (i > bufferReadResult / 2) winval = 0;
            fftRealArray[bufferReadResult / 2 + i] *= winval;
            fftRealArray[bufferReadResult / 2 - i] *= winval;
            fftRealArray2[bufferReadResult / 2 + i] *= winval;
            fftRealArray2[bufferReadResult / 2 - i] *= winval;
        }

        // zero out first point (not touched by odd-length window)
        //fftRealArray[0] = 0;

        fft.forward(fftRealArray);
        fft2.forward(fftRealArray2);

        //apply smoothing
        smoothOutFreqs();
    }

    // compute nearest higher power of two
    // see: graphics.stanford.edu/~seander/bithacks.html
    int getHigherP2(int val) {
        val--;
        val |= val >> 1;
        val |= val >> 2;
        val |= val >> 8;
        val |= val >> 16;
        val++;
        return (val);
    }
}