package com.mydoppler.ToolPackage;

/**
 * Created by Li Lu on 2017/4/26.
 */

public class StatPars {
    public static boolean allZero(float[] a) {
        boolean flag = true;
        for (double i : a)
            if (i != 0) {
                flag = false;
                break;
            }
        return flag;
    }
    public static double getCorrelation(double[] a, double[] b) {
        double rho = getCovariance(a, b) / (getVariance(a) * getVariance(b));
        return rho;
    }
    private static double getMean(double[] a) {
        double meanvalue = 0;
        for (int i = 0; i < a.length; i++)
            meanvalue += a[i];
        meanvalue /= a.length;
        return meanvalue;
    }
    private static double getVariance(double[] a) {
        double meanvalue = getMean(a);
        double variance = 0;
        for (int i = 0; i < a.length; i++) {
            variance += Math.pow((a[i] - meanvalue), 2);
        }
        variance /= a.length;
        variance = Math.sqrt(variance);
        return variance;
    }
    private static double getCovariance(double[] a, double[] b) {
        double meana = getMean(a);
        double meanb = getMean(b);
        short covariance = 0;
        for (int i = 0; i < a.length; i++) {
            covariance += (a[i] - meana) * (b[i] - meanb);
        }
        covariance /= a.length;
        return covariance;
    }

    public static double[] minus(double[] a, double[] b){
        double[] tmp=new double[a.length];
        for (int i=0;i<a.length;i++){
            tmp[i]=b[i]-a[i];
        }
        return tmp;
    }
}
