package com.mydoppler.ToolPackage;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.signum;



public class Calibrator {
    private double previousDiff = 0;
    private int previousDirection = 0;
    private double directionChanges = 0;
    private int iteration = 0;

    private final int ITERATION_CYCLES = 20;

    private final int UP_THRESHOLD= 5;
    private final int DOWN_THRESHOLD = 0;
    private final double UP_AMOUNT = 1.1;
    private final double DOWN_AMOUNT = 0.9;

    private final double MAX_RATIO = 0.95;
    private final double MIN_RATIO = 0.0001;

    public double calibrate(double maxVolumeRatio, int leftBandwidth, int rightBandwidth) {
        int difference = leftBandwidth - rightBandwidth;
        int direction = (int) signum(difference);

        if (previousDirection != direction) {
            directionChanges++;
            //Log.d("CALIBRATE", "increase directionchanges");
            previousDirection = direction;
        }

        iteration = ++iteration % ITERATION_CYCLES;
        if (iteration == 0) {
            //Log.d("Direction changes", directionChanges + "");
            if (directionChanges >= UP_THRESHOLD) {
                maxVolumeRatio = maxVolumeRatio * UP_AMOUNT;
            }
            if (directionChanges == DOWN_THRESHOLD) {
                maxVolumeRatio = maxVolumeRatio * DOWN_AMOUNT;
            }

            maxVolumeRatio = min(MAX_RATIO, maxVolumeRatio);
            maxVolumeRatio = max(MIN_RATIO, maxVolumeRatio);
            directionChanges = 0;
        }

        return maxVolumeRatio;
    }

}
