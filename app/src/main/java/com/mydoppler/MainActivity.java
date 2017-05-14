package com.mydoppler;

import android.graphics.Color;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.mydoppler.ToolPackage.Doppler;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import com.mydoppler.ToolPackage.StatPars;

public class MainActivity extends AppCompatActivity {

    Button start, stop;
    private boolean stopFlag = false;
    private boolean startFlag = false;
    EditText csvData;
    private boolean isFirstSet = true;

    private XYSeries mSeries;
    private XYSeries baseLine;
    private XYSeries thdLine;
    private GraphicalView mChart;
    private final XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();
    private XYSeriesRenderer mRenderer;
    private XYSeriesRenderer baseLineRenderer;
    private XYSeriesRenderer thdLineRenderer;

    private final XYMultipleSeriesRenderer renderer = new XYMultipleSeriesRenderer();

    private Doppler doppler;

    File file;
    FileOutputStream fos;
    PrintWriter pw;

    private static long currentTime;
    private static long startTime;

    private static final int leftIndex = 1850; //
    private static final int rightIndex = 1865; //
    private double[] dop;
    private double[] dop2;
    private double bound = 5;

    private boolean isFirst;

    private Handler mHandler;
    private Runnable mRunnable;

    private double[] old_dop;
    private double[] old_dop2;
    private double[] cor;
    private double[] cor2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        csvData = (EditText) findViewById(R.id.editText);
        start = (Button) findViewById(R.id.start_button);
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                    try {
                        isFirstSet = true;
                        file = new File(Environment.getExternalStorageDirectory().getPath(), "Outputing/" + csvData.getText() + "c.csv");
                        file.createNewFile();
                        pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file))));

                        pw.write("currentTime");
                        for (int i = leftIndex; i <= rightIndex; ++i) {
                            pw.write(",bins[" + i + "]");
                        }
                        pw.write("\n");
                        pw.flush();

                        Toast.makeText(getBaseContext(), "Start recoding the data set", Toast.LENGTH_LONG).show();

                        isFirst = true;
                        dop = new double[rightIndex - leftIndex + 1];
                        old_dop = new double[rightIndex - leftIndex + 1];
                        dop2 = new double[rightIndex - leftIndex + 1];
                        old_dop2 = new double[rightIndex - leftIndex + 1];
                        for (int count = 0; count < rightIndex - leftIndex + 1; count++) {
                            old_dop[count] = 0;
                            old_dop2[count] = 0;
                        }
                        cor = new double[rightIndex - leftIndex + 1];
                        cor2 = new double[rightIndex - leftIndex + 1];
                        mHandler = new Handler();
                        mRunnable = new Runnable() {
                            @Override
                            public void run() {
                                recordData();
                                mHandler.post(this);
                            }
                        };

                        fos = new FileOutputStream(file);
                        pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(fos)));

                        pw.write("currentTime");
                        for (int i = leftIndex; i <= rightIndex; ++i) {
                            pw.write(",bins[" + i + "]");
                        }
                        pw.write("\n");

                        doppler = TheDoppler.getDoppler();
                        doppler.start();
                    } catch (Exception e) {
                        Toast.makeText(getBaseContext(), e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    } finally {
                        updateDoppler();

                        startFlag = true;
                        csvData.setClickable(false);
                        start.setClickable(false);
                        stop.setClickable(true);
                        csvData.setFocusable(false);
                        renderGraph();
                        startGraph();
                        mHandler.post(mRunnable);
                    }
                }
            }
        });

        stop = (Button) findViewById(R.id.stop_button);
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    stopFlag = true;
                    doppler.pause();
                    Toast.makeText(getBaseContext(), "Done recording the data set", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(getBaseContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                } finally {
                    startFlag = false;
                    stop.setClickable(false);
                    start.setClickable(true);
                    isFirstSet = true;
                    csvData.setFocusableInTouchMode(true);
//                    updateDoppler();
                    //mHandler.removeCallbacks(mRunnable);
                    dop = new double[19];
                    old_dop = new double[19];
                    dop2 = new double[19];
                    old_dop2 = new double[19];
                    for (int count = 0; count < 19; count++) {
                        old_dop[count] = 0;
                        old_dop2[count] = 0;
                    }
                    doppler.removeReadCallback();
                    mSeries.clear();
                    baseLine.clear();
                    ((LinearLayout) findViewById(R.id.chart)).removeView(mChart);
                }
            }
        });
    }

    public void updateDoppler() {
        doppler.setOnReadCallback(new Doppler.OnReadCallback() {
            @Override
            public void onBandwidthRead(int leftBandwidth, int rightBandwidth) {

            }

            @Override
            public void onBinsRead(double[] bins, double[] bins2) {
                for (int i = leftIndex; i < rightIndex + 1; ++i) {
                    dop[i - leftIndex] = bins[i];
                    dop2[i - leftIndex] = bins2[i];
                }
                cor = StatPars.minus(dop, old_dop);
                cor2 = StatPars.minus(dop2, old_dop2);
                old_dop = (double[]) dop.clone();
                old_dop2 = (double[]) dop2.clone();
            }
        });
    }

    private void startGraph() {
        doppler.setOnReadCallback(new Doppler.OnReadCallback() {
            @Override
            public void onBandwidthRead(int leftBandwidth, int rightBandwidth) {

            }

            @Override
            public void onBinsRead(double[] bins, double[] bins2) {
                mSeries.clear();
                baseLine.clear();
                for (int i = leftIndex; i <= rightIndex; ++i) {
                    dop[i - leftIndex] = bins[i];
                    cor[i - leftIndex] = dop[i - leftIndex] - old_dop[i - leftIndex];
                    old_dop[i - leftIndex] = dop[i - leftIndex];
                    dop2[i - leftIndex] = bins2[i];
                    cor2[i - leftIndex] = dop2[i - leftIndex] - old_dop2[i - leftIndex];
                    old_dop2[i - leftIndex] = dop2[i - leftIndex];
                    baseLine.add(i, cor2[i-leftIndex]);
                    //mSeries.add(i, bins[i]);
                    mSeries.add(i, cor[i - leftIndex]);
                }
                mChart.repaint();
            }
        });
    }


    private void recordData() {
        if (isFirst) {
            startTime = System.currentTimeMillis();
            isFirst = false;
        }
        long lastTime = isFirst ? startTime : currentTime;
        currentTime = System.currentTimeMillis();
        String s = currentTime + "";
        for (int i = leftIndex; i <= rightIndex; ++i) {
            s += "," + cor[i - leftIndex];
        }
        int x = rightIndex - leftIndex;
        //Log.i("Record","data length "+x);
        pw.write(s + "\n");
        //Log.i("Time:", "delta: " + (currentTime - lastTime) + " " + s);
    }


    private void renderGraph() {
        mSeries = new XYSeries("Sigmoid");
        baseLine = new XYSeries("baseLine");
        thdLine = new XYSeries("Threshold");
        mSeries.add(3, 4);
        mRenderer = new XYSeriesRenderer();
        baseLineRenderer = new XYSeriesRenderer();
        thdLineRenderer = new XYSeriesRenderer();
        baseLineRenderer.setColor(getResources().getColor(R.color.red));
        thdLineRenderer.setColor(getResources().getColor(R.color.black));
        dataset.addSeries(mSeries);
        dataset.addSeries(baseLine);
        dataset.addSeries(thdLine);
        renderer.addSeriesRenderer(mRenderer);
        renderer.addSeriesRenderer(baseLineRenderer);
        renderer.addSeriesRenderer(thdLineRenderer);
        renderer.setPanEnabled(false);
        renderer.setZoomEnabled(false);
        renderer.setYAxisMin(-bound);
        renderer.setYAxisMax(bound);
        renderer.setShowGrid(true);
        mChart = null;
        mChart = ChartFactory.getLineChartView(this, dataset, renderer);

        ((LinearLayout) findViewById(R.id.chart)).addView(mChart);
    }

}
