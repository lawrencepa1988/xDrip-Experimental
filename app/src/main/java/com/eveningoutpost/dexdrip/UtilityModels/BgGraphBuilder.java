package com.eveningoutpost.dexdrip.UtilityModels;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.eveningoutpost.dexdrip.Models.BgReading;
import com.eveningoutpost.dexdrip.Models.Calibration;
import com.eveningoutpost.dexdrip.R;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import lecho.lib.hellocharts.listener.LineChartOnValueSelectListener;
import lecho.lib.hellocharts.model.Axis;
import lecho.lib.hellocharts.model.AxisValue;
import lecho.lib.hellocharts.model.Line;
import lecho.lib.hellocharts.model.LineChartData;
import lecho.lib.hellocharts.model.PointValue;
import lecho.lib.hellocharts.model.Viewport;
import lecho.lib.hellocharts.util.ChartUtils;
import lecho.lib.hellocharts.view.Chart;

/**
 * Created by stephenblack on 11/15/14.
 */
public class BgGraphBuilder {
    public static final int FUZZER = (1000 * 30 * 5);
    public long  end_time;
    public long  start_time;
    public Context context;
    public SharedPreferences prefs;
    public double highMark;
    public double lowMark;
    public double defaultMinY;
    public double defaultMaxY;
    public boolean doMgdl;
    final int pointSize;
    final int axisTextSize;
    final int previewAxisTextSize;
    final int hoursPreviewStep;
    private double endHour;

    private static final int NUM_VALUES =(60/5)*24;
    private final List<BgReading> bgReadings;
    private final List<Calibration> calibrations;
    private List<PointValue> inRangeValues = new ArrayList<PointValue>();
    private List<PointValue> highValues = new ArrayList<PointValue>();
    private List<PointValue> lowValues = new ArrayList<PointValue>();
    private List<PointValue> rawInterpretedValues = new ArrayList<PointValue>();
    private List<PointValue> calibrationValues = new ArrayList<PointValue>();
    static final boolean LINE_VISIBLE = true;
    static final boolean FILL_UNDER_LINE = false;
    public Viewport viewport;


    public BgGraphBuilder(Context context){
        this(context, new Date().getTime() + (60000 * 10));

    }

    public BgGraphBuilder(Context context, long end){
        this(context, end - (60000 * 60 * 24), end);
    }

    public BgGraphBuilder(Context context, long start, long end){
        this(context, start, end, NUM_VALUES);
    }

    public BgGraphBuilder(Context context, long start, long end, int numValues){
        end_time = end;
        start_time = start;
        bgReadings = BgReading.latestForGraph( numValues, start, end);
        calibrations = Calibration.latestForGraph( numValues, start, end);
        this.context = context;
        this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
        this.highMark = Double.parseDouble(prefs.getString("highValue", "170"));
        this.lowMark = Double.parseDouble(prefs.getString("lowValue", "70"));
        this.doMgdl = (prefs.getString("units", "mgdl").equals("mgdl"));
        defaultMinY = unitized(40);
        defaultMaxY = unitized(250);
        pointSize = isXLargeTablet(context) ? 5 : 3;
        axisTextSize = isXLargeTablet(context) ? 20 : Axis.DEFAULT_TEXT_SIZE_SP;
        previewAxisTextSize = isXLargeTablet(context) ? 12 : 5;
        hoursPreviewStep = isXLargeTablet(context) ? 2 : 1;
    }

    public LineChartData lineData() {
        LineChartData lineData = new LineChartData(defaultLines());
        lineData.setAxisYLeft(yAxis());
        lineData.setAxisXBottom(chartXAxis());
        return lineData;
    }

    public LineChartData previewLineData() {
        LineChartData previewLineData = new LineChartData(lineData());
        previewLineData.setAxisYLeft(previewYAxis());
        previewLineData.setAxisXBottom(previewXAxis());
        previewLineData.getLines().get(5).setPointRadius(2);
        previewLineData.getLines().get(6).setPointRadius(2);
        previewLineData.getLines().get(7).setPointRadius(2);
        return previewLineData;
    }

    public List<Line> defaultLines() {
        addBgReadingValues();
        List<Line> lines = new ArrayList<Line>();
        Line[] calib = calibrationValuesLine();
        lines.add(calib[0]); // white circle of calib in background
        lines.add(minShowLine());
        lines.add(maxShowLine());
        lines.add(highLine());
        lines.add(lowLine());
        lines.add(inRangeValuesLine());
        lines.add(lowValuesLine());
        lines.add(highValuesLine());
        lines.add(rawInterpretedLine());
        lines.add(calib[1]); // red dot of calib in foreground
        return lines;
    }

    public Line highValuesLine() {
        Line highValuesLine = new Line(highValues);
        highValuesLine.setColor(Color.parseColor("#FFFF00"));
        highValuesLine.setHasLines(false);
        highValuesLine.setPointRadius(pointSize);
        highValuesLine.setHasPoints(true);
        return highValuesLine;
    }

    public Line lowValuesLine() {
        Line lowValuesLine = new Line(lowValues);
        lowValuesLine.setColor(Color.parseColor("#FF0000"));
        lowValuesLine.setHasLines(false);
        lowValuesLine.setPointRadius(pointSize);
        lowValuesLine.setHasPoints(true);
        return lowValuesLine;
    }

    public Line inRangeValuesLine() {
        Line inRangeValuesLine = new Line(inRangeValues);
        inRangeValuesLine.setColor(Color.parseColor("#00FF00"));
        inRangeValuesLine.setHasLines(false);
        inRangeValuesLine.setPointRadius(pointSize);
        inRangeValuesLine.setHasPoints(true);
        return inRangeValuesLine;
    }


    public Line rawInterpretedLine() {
        Line line = new Line(rawInterpretedValues);
        line.setHasLines(false);
        line.setPointRadius(1);
        line.setHasPoints(true);
        return line;
    }

    public Line[] calibrationValuesLine() {
        Line[] lines = new Line[2];
        lines[0] = new Line(calibrationValues);
        lines[0].setColor(Color.parseColor("#FFFFFF"));
        lines[0].setHasLines(false);
        lines[0].setPointRadius(pointSize * 3 / 2);
        lines[0].setHasPoints(true);
        lines[1] = new Line(calibrationValues);
        lines[1].setColor(ChartUtils.COLOR_RED);
        lines[1].setHasLines(false);
        lines[1].setPointRadius(pointSize * 3 / 4);
        lines[1].setHasPoints(true);
        return lines;
    }


    private void addBgReadingValues() {
        for (BgReading bgReading : bgReadings) {
            if (bgReading.raw_calculated != 0 && prefs.getBoolean("interpret_raw", false)) {
                rawInterpretedValues.add(new PointValue((float) (bgReading.timestamp/ FUZZER), (float) unitized(bgReading.raw_calculated)));
            } else if (bgReading.calculated_value >= 400) {
                highValues.add(new PointValue((float) (bgReading.timestamp/ FUZZER), (float) unitized(400)));
            } else if (unitized(bgReading.calculated_value) >= highMark) {
                highValues.add(new PointValue((float) (bgReading.timestamp/ FUZZER), (float) unitized(bgReading.calculated_value)));
            } else if (unitized(bgReading.calculated_value) >= lowMark) {
                inRangeValues.add(new PointValue((float) (bgReading.timestamp/ FUZZER), (float) unitized(bgReading.calculated_value)));
            } else if (bgReading.calculated_value >= 40) {
                lowValues.add(new PointValue((float)(bgReading.timestamp/ FUZZER), (float) unitized(bgReading.calculated_value)));
            } else if (bgReading.calculated_value > 13) {
                lowValues.add(new PointValue((float)(bgReading.timestamp/ FUZZER), (float) unitized(40)));
            }
        }
        for (Calibration calibration : calibrations) {
            calibrationValues.add(new PointValue((float)(calibration.timestamp/ FUZZER), (float) unitized(calibration.bg)));
        }
    }

    public Line highLine(){ return highLine(LINE_VISIBLE);}

    public Line highLine(boolean show) {
        List<PointValue> highLineValues = new ArrayList<PointValue>();
        highLineValues.add(new PointValue((float) start_time / FUZZER, (float) highMark));
        highLineValues.add(new PointValue((float) end_time / FUZZER, (float) highMark));
        Line highLine = new Line(highLineValues);
        highLine.setHasPoints(false);

        highLine.setStrokeWidth(1);
        if(show) {
            highLine.setColor(Color.parseColor("#FFFF00"));
        } else {
            highLine.setColor(Color.TRANSPARENT);
        }
        return highLine;
    }

    public Line lowLine(){ return lowLine(LINE_VISIBLE, FILL_UNDER_LINE);}

    public Line lowLine(boolean show, boolean line_only) {
        List<PointValue> lowLineValues = new ArrayList<PointValue>();
        lowLineValues.add(new PointValue((float)start_time / FUZZER, (float)lowMark));
        lowLineValues.add(new PointValue((float) end_time / FUZZER, (float) lowMark));
        Line lowLine = new Line(lowLineValues);
        lowLine.setHasPoints(false);
        if(!line_only) {
            lowLine.setAreaTransparency(20);
            lowLine.setFilled(true);
        }
        lowLine.setStrokeWidth(1);
        if(show){
            lowLine.setColor(Color.parseColor("#FF0000"));
        } else {
            lowLine.setColor(Color.TRANSPARENT);
        }
        return lowLine;
    }

    public Line maxShowLine() {
        List<PointValue> maxShowValues = new ArrayList<PointValue>();
        maxShowValues.add(new PointValue((float) start_time / FUZZER, (float) defaultMaxY));
        maxShowValues.add(new PointValue((float) end_time / FUZZER, (float) defaultMaxY));
        Line maxShowLine = new Line(maxShowValues);
        maxShowLine.setHasLines(false);
        maxShowLine.setHasPoints(false);
        return maxShowLine;
    }

    public Line minShowLine() {
        List<PointValue> minShowValues = new ArrayList<PointValue>();
        minShowValues.add(new PointValue((float) start_time / FUZZER, (float) defaultMinY));
        minShowValues.add(new PointValue((float) end_time / FUZZER, (float) defaultMinY));
        Line minShowLine = new Line(minShowValues);
        minShowLine.setHasPoints(false);
        minShowLine.setHasLines(false);
        return minShowLine;
    }

    /////////AXIS RELATED//////////////
    public Axis previewYAxis() {
        Axis yAxis = new Axis();
        yAxis.setAutoGenerated(false);
        List<AxisValue> axisValues = new ArrayList<AxisValue>();

//        for(int j = 1; j <= 12; j += 1) {
//            if (doMgdl) {
//                axisValues.add(new AxisValue(j * 50));
//            } else {
//                axisValues.add(new AxisValue(j*2));
//            }
//        }
        axisValues.add(new AxisValue((int) this.lowMark));
        axisValues.add(new AxisValue((int) this.highMark));
        yAxis.setValues(axisValues);
        yAxis.setHasLines(true);
        yAxis.setLineColor(Color.parseColor("#555555"));
        yAxis.setTextSize(0);
        yAxis.setInside(true);


        return yAxis;
    }

    public Axis yAxis() {
        Axis yAxis = new Axis();
        yAxis.setAutoGenerated(false);
        List<AxisValue> axisValues = new ArrayList<AxisValue>();

//        for(int j = 1; j <= 12; j += 1) {
//            if (doMgdl) {
//                axisValues.add(new AxisValue(j * 50));
//            } else {
//                axisValues.add(new AxisValue(j*2));
//            }
//        }
        axisValues.add(new AxisValue(240));
        axisValues.add(new AxisValue((int) this.lowMark));
        axisValues.add(new AxisValue((int) this.highMark));
        yAxis.setValues(axisValues);
        yAxis.setHasLines(true);
        yAxis.setLineColor(Color.parseColor("#555555"));
        yAxis.setMaxLabelChars(5);
        yAxis.setInside(true);
        yAxis.setTextSize(axisTextSize);
        yAxis.setTextColor(Color.parseColor("#8D8D8D"));
        yAxis.setHasTiltedLabels(true);

        return yAxis;
    }

    public Axis chartXAxis() {
        Axis xAxis = xAxis();
        xAxis.setTextSize(axisTextSize);
        return xAxis;
    }

    private SimpleDateFormat hourFormat() {
        return new SimpleDateFormat(DateFormat.is24HourFormat(context) ? "HH" : "h a");
    }

    // Please note, an xLarge table is also large, but a small one is only small.
    static public boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }
    static public boolean isLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    public Axis previewXAxis(){
        List<AxisValue> previewXaxisValues = new ArrayList<AxisValue>();
        final java.text.DateFormat timeFormat = hourFormat();
        timeFormat.setTimeZone(TimeZone.getDefault());
        for(int l=0; l<=24; l+=hoursPreviewStep) {
            double timestamp = (endHour - (60000 * 60 * l));
            previewXaxisValues.add(new AxisValue((long)(timestamp/FUZZER), (timeFormat.format(timestamp)).toCharArray()));
        }
        Axis previewXaxis = new Axis();
        previewXaxis.setValues(previewXaxisValues);
        previewXaxis.setHasLines(true);
        previewXaxis.setTextSize(previewAxisTextSize);
        previewXaxis.setLineColor(Color.parseColor("#555555"));
        previewXaxis.setTextSize(axisTextSize);
        return previewXaxis;
    }

    @NonNull
    private Axis xAxis() {
        Axis xAxis = new Axis();
        xAxis.setAutoGenerated(false);
        List<AxisValue> xAxisValues = new ArrayList<AxisValue>();
        GregorianCalendar now = new GregorianCalendar();
        GregorianCalendar today = new GregorianCalendar(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        final java.text.DateFormat timeFormat = hourFormat();
        timeFormat.setTimeZone(TimeZone.getDefault());
        double start_hour_block = today.getTime().getTime();
        double timeNow = new Date().getTime();
        for(int l=0; l<=24; l++) {
            if ((start_hour_block + (60000 * 60 * (l))) <  timeNow) {
                if((start_hour_block + (60000 * 60 * (l + 1))) >=  timeNow) {
                    endHour = start_hour_block + (60000 * 60 * (l));
                    l=25;
                }
            }
        }
        for(int l=0; l<=24; l++) {
            double timestamp = (endHour - (60000 * 60 * l));
            xAxisValues.add(new AxisValue((long)(timestamp/FUZZER), (timeFormat.format(timestamp)).toCharArray()));
        }
        xAxis.setValues(xAxisValues);
        xAxis.setHasLines(true);
        xAxis.setLineColor(Color.parseColor("#555555"));
        xAxis.setTextSize(axisTextSize);
        return xAxis;
    }

    /////////VIEWPORT RELATED//////////////
    public Viewport advanceViewport(Chart chart, Chart previewChart) {
        viewport = new Viewport(previewChart.getMaximumViewport());
        viewport.inset((float) ((86400000 / 2.5) / FUZZER), 0);
        double distance_to_move = ((new Date().getTime())/ FUZZER) - viewport.left - (((viewport.right - viewport.left) /2));
        viewport.offset((float) distance_to_move, 0);
        return viewport;
    }

    public double unitized(double value) {
        if(doMgdl) {
            return value;
        } else {
            return mmolConvert(value);
        }
    }

    public String unitized_string(double value) {
        DecimalFormat df = new DecimalFormat("#");
        if (value >= 400) {
            return "HIGH";
        } else if (value >= 40) {
            if(doMgdl) {
                df.setMaximumFractionDigits(0);
                return df.format(value);
            } else {
                df.setMaximumFractionDigits(1);
                //next line ensures mmol/l value is XX.x always.  Required by PebbleSync, and probably not a bad idea.
                df.setMinimumFractionDigits(1);
                return df.format(mmolConvert(value));
            }
        } else if (value > 12) {
            return "LOW";
        } else {
            switch((int)value) {
                case 0:
                    return "??0";
                case 1:
                    return "?SN";
                case 2:
                    return "??2";
                case 3:
                    return "?NA";
                case 5:
                    return "?NC";
                case 6:
                    return "?CD";
                case 9:
                    return "?AD";
                case 12:
                    return "?RF";
                default:
                    return "???";
            }
        }
    }

    public String unitizedDeltaString(boolean showUnit, boolean highGranularity) {

        List<BgReading> last2 = BgReading.latest(2);
        if(last2.size() < 2 || last2.get(0).timestamp - last2.get(1).timestamp > 20 * 60 * 1000){
            // don't show delta if there are not enough values or the values are more than 20 mintes apart
            return "???";
        }

        double value = BgReading.currentSlope() * 5*60*1000;

        if(Math.abs(value) > 100){
            // a delta > 100 will not happen with real BG values -> problematic sensor data
            return "ERR";
        }

        // TODO: allow localization from os settings once pebble doesn't require english locale
        DecimalFormat df = new DecimalFormat("#", new DecimalFormatSymbols(Locale.ENGLISH));
        String delta_sign = "";
        if (value > 0) { delta_sign = "+"; }
        if(doMgdl) {

            if(highGranularity){
                df.setMaximumFractionDigits(1);
            } else {
                df.setMaximumFractionDigits(0);
            }

            return delta_sign + df.format(unitized(value)) +  (showUnit?" mg/dL":"");
        } else {

            if(highGranularity){
                df.setMaximumFractionDigits(2);
            } else {
                df.setMaximumFractionDigits(1);
            }

            df.setMinimumFractionDigits(1);
            df.setMinimumIntegerDigits(1);
            return delta_sign + df.format(unitized(value)) + (showUnit?" mmol/L":"");
        }
    }


    public static double mmolConvert(double mgdl) {
        return mgdl * Constants.MGDL_TO_MMOLL;
    }

    public String unit() {
        if(doMgdl){
            return "mg/dL";
        } else {
            return "mmol/L";
        }

    }

    public OnValueSelectTooltipListener getOnValueSelectTooltipListener(){
        return new OnValueSelectTooltipListener();
    }

    public class OnValueSelectTooltipListener implements LineChartOnValueSelectListener{

        private Toast tooltip;// = Toast.makeText(context, resTxtId, Toast.LENGTH_LONG);

        @Override
        public synchronized void onValueSelected(int i, int i1, PointValue pointValue) {
            final java.text.DateFormat timeFormat = DateFormat.getTimeFormat(context);
            //Won't give the exact time of the reading but the time on the grid: close enough.
            Long time = ((long)pointValue.getX())*FUZZER;
            if(tooltip!= null){
                tooltip.cancel();
            }
            tooltip = Toast.makeText(context, timeFormat.format(time)+ ": " + Math.round(pointValue.getY()*10)/ 10d , Toast.LENGTH_LONG);
            View view = tooltip.getView();
            view.setBackgroundColor(Color.parseColor("#212121"));

            tooltip.show();
        }

        @Override
        public void onValueDeselected() {
            // do nothing
        }
    }
}
