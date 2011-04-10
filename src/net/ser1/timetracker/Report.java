package net.ser1.timetracker;

import static net.ser1.timetracker.Tasks.REPORT_DATE;
import static net.ser1.timetracker.Tasks.START_DAY;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.ser1.timetracker.reports.ReportModel;
import net.ser1.timetracker.reports.TableView;
import net.ser1.timetracker.reports.TagReport;
import net.ser1.timetracker.reports.TaskReport;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.TextView;



public class Report extends Activity implements OnClickListener {
    public static final String REPORT_FORMAT = "report-format";
    /**
     * Defines how each task's time is displayed 
     */
    private Calendar weekStart, weekEnd;
    private TextView weekView;
    private static final SimpleDateFormat WEEK_FORMAT = new SimpleDateFormat("w");
    private static final SimpleDateFormat TITLE_FORMAT = new SimpleDateFormat("EEE, MMM d");
    private static final int SELECT_REPORT = 1;
    private static final int TASK_REPORT = 0, TAG_REPORT = 1;
    private DBHelper dbHelper;
    private int startDay;
    private boolean decimalTime = false;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        Calendar c = Calendar.getInstance();
        c.setFirstDayOfWeek( Calendar.MONDAY );
        Bundle extras = getIntent().getExtras();
        c.setTimeInMillis(extras.getLong(REPORT_DATE));
        startDay = extras.getInt(START_DAY);
        weekStart = weekStart(c, startDay);
        weekEnd = weekEnd(c, startDay);
        String beginning = TITLE_FORMAT.format(weekStart.getTime());
        String ending = TITLE_FORMAT.format(weekEnd.getTime());
        String title = getString(R.string.report_title, beginning, ending);
        setTitle( title );
        decimalTime = extras.getBoolean(Tasks.TIMEDISPLAY);
        
        preferences = getSharedPreferences( Tasks.TIMETRACKERPREF, MODE_PRIVATE);

        dbHelper = new DBHelper(this);
        
        switchReport();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, Tasks.EXPORT_VIEW, 0, R.string.export_view)
            .setIcon(android.R.drawable.ic_menu_save);
        menu.add(0, SELECT_REPORT, 2, R.string.select_report)
            .setIcon(android.R.drawable.ic_menu_agenda);
        return true;
    }

    private AlertDialog exportSucceed;
    private String exportMessage;
    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch (item.getItemId()) {
        case Tasks.EXPORT_VIEW:
            String fname = export();
            if (fname != null) {
                exportMessage = getString(R.string.export_csv_success, fname);
                if (exportSucceed != null) exportSucceed.setMessage(exportMessage);
                showDialog(Tasks.SUCCESS_DIALOG);
            } else {
                exportMessage = getString(R.string.export_csv_fail);
                showDialog(Tasks.ERROR_DIALOG);
            }
            break;
        case SELECT_REPORT:
            showDialog(SELECT_REPORT);
            break;
        default:
            // Ignore the other menu items; they're context menu
            break;
        }
        return super.onMenuItemSelected(featureId, item);
    }
    
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
        case Tasks.SUCCESS_DIALOG:
            exportSucceed = new AlertDialog.Builder(this)
            .setTitle(R.string.success)
            .setIcon(android.R.drawable.stat_notify_sdcard)
            .setMessage(exportMessage)
            .setPositiveButton(android.R.string.ok, null)
            .create();
            return exportSucceed;
        case Tasks.ERROR_DIALOG:
            return new AlertDialog.Builder(this)
            .setTitle(R.string.failure)
            .setIcon(android.R.drawable.stat_notify_sdcard)
            .setMessage(exportMessage)
            .setPositiveButton(android.R.string.ok, null)
            .create();
        case SELECT_REPORT:
            return new AlertDialog.Builder(this).setItems(R.array.reports, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    Editor prefEdit = preferences.edit();
                    prefEdit.putInt(REPORT_FORMAT, which);
                    prefEdit.commit();
                    switchReport();
                }
            }).create();
        default:
            break;
        }
        return null;
    }


    private void switchReport() {
        int reportFormat = preferences.getInt(Report.REPORT_FORMAT, TASK_REPORT);
        reportModel = null;
        switch (reportFormat) {
        case TASK_REPORT:
            reportModel = new TaskReport();
            break;
        case TAG_REPORT:
            reportModel = new TagReport();
            break;
        default:
            // Ok, this is bad... but let's try to be calm about it
            Logger.getLogger("TimeTracker").log(Level.SEVERE,
                    "Invalid report format "+reportFormat+
                    "!  This should be filed as a bug.  Defaulting to the task report" );
            reportModel = new TaskReport();
            break;            
        }

        reportModel.setContext(this);
        reportModel.setDbHelper(dbHelper);
        reportModel.setDecimalTime(decimalTime);
        reportModel.setStartDay(startDay);
        reportModel.setWeekStart(weekStart);
        reportModel.setWeekEnd(weekEnd);            

        layoutView = new TableView(this);
        layoutView.createHeader( reportModel.columnNames() );
        layoutView.createReport( reportModel.rowNames() );
        layoutView.fillInReport( reportModel.rows() );
        setContentView(layoutView.getView());
        
        // FIXME: This is a problem... need to abstract this out
        ((ImageButton)layoutView.getView().findViewById(R.id.decrement_week)).setOnClickListener(this);
        ((ImageButton)layoutView.getView().findViewById(R.id.increment_week)).setOnClickListener(this);
        weekView = (TextView)layoutView.getView().findViewById(R.id.week);
        weekView.setText(getString(R.string.week,WEEK_FORMAT.format(weekStart.getTime())));
    }

    /**
     * Yes, this _is_ a duplicate of the exact same code in Tasks.  Java doesn't 
     * support mix-ins, which leads to bad programming practices out of 
     * necessity.
     */
    final static String SDCARD = "/sdcard/";
    private ReportModel reportModel;
    private TableView layoutView;
    private String export() {
        // Export, then show a dialog
        String rangeName = getRangeName();
        String fname = "report_"+rangeName+".csv";
        File fout = new File( SDCARD+fname );
        // Change the file name until there's no conflict
        int counter = 0;
        while (fout.exists()) {
            fname = "report_"+rangeName+"_"+counter+".csv";
            fout = new File( SDCARD+fname );
            counter++;
        }
        try {
            OutputStream out = new FileOutputStream(fout);
            String[]   cols = reportModel.columnNames();
            String[]   rows = reportModel.rowNames();
            String[][] data = reportModel.rows();
            String[][] toExport = new String[rows.length + 1][cols.length + 1];
            System.arraycopy(cols, 0, toExport[0], 0, cols.length);
            // In the following, remember, in terms of the output array (row,col):
            // 1. cols are from (0,0) to the end (all columns)
            // 2. rows start at (1,0) and are the contents of the 0'th column
            // 3. data starts at (1,1)
            for (int rowIdx = 0; rowIdx < rows.length; rowIdx++) {
                toExport[rowIdx+1][0] = rows[rowIdx];
                for (int colIdx = 0; colIdx < cols.length; colIdx++) {
                    System.arraycopy(data[rowIdx], 0, toExport[rowIdx+1], 1, data[rowIdx].length);
                }
            }
            CSVExporter.exportRows(out, toExport);
            
            return fname;
        } catch (FileNotFoundException fnfe) {
            fnfe.printStackTrace(System.err);
            return null;
        }
    }
    
    private String getRangeName() {
        return String.format("%1$tF", weekStart.getTime());
    }

    /**
     * Calculates the date/time of the beginning of the week in 
     * which the supplied calendar date falls
     * @param tw the day for which to calculate the week start
     * @param startDay the day on which the week starts.  This must be 1-based
     * (1 = Sunday).
     * @return a Calendar marking the start of the week
     */
    public static Calendar weekStart(Calendar tw, int startDay) {
        Calendar ws = (Calendar)tw.clone();
        ws.setFirstDayOfWeek( startDay );
        // START ANDROID BUG WORKAROUND
        // Android has a broken Calendar class, so the if-statement wrapping
        // the following set() is necessary to keep Android from incorrectly
        // changing the date:
        int adjustedDay = ws.get(Calendar.DAY_OF_WEEK);
        ws.add(Calendar.DATE, -((7-(startDay-adjustedDay)) % 7));
        // The above code _should_ be:
        // ws.set(Calendar.DAY_OF_WEEK, startDay);
        // END ANDROID BUG WORKAROUND
        ws.set(Calendar.HOUR_OF_DAY, ws.getMinimum(Calendar.HOUR_OF_DAY));
        ws.set(Calendar.MINUTE, ws.getMinimum(Calendar.MINUTE));
        ws.set(Calendar.SECOND, ws.getMinimum(Calendar.SECOND));
        ws.set(Calendar.MILLISECOND, ws.getMinimum(Calendar.MILLISECOND));
        return ws;
    }

    /**
     * Calculates the date/time of the end of the week in 
     * which the supplied calendar data falls
     * @param tw the day for which to calculate the week end
     * @return a Calendar marking the end of the week
     */
    public static Calendar weekEnd(Calendar tw, int startDay) {
        Calendar ws = (Calendar)tw.clone();
        ws.setFirstDayOfWeek( startDay );
        // START ANDROID BUG WORKAROUND
        // Android has a broken Calendar class, so the if-statement wrapping
        // the following set() is necessary to keep Android from incorrectly
        // changing the date:
        int adjustedDay = ws.get(Calendar.DAY_OF_WEEK);
        ws.add(Calendar.DATE, -((7-(startDay-adjustedDay))%7));
        // The above code _should_ be:
        // ws.set(Calendar.DAY_OF_WEEK, startDay);
        // END ANDROID BUG WORKAROUND
        ws.add(Calendar.DAY_OF_WEEK, 6);
        ws.set(Calendar.HOUR_OF_DAY, ws.getMaximum(Calendar.HOUR_OF_DAY));
        ws.set(Calendar.MINUTE, ws.getMaximum(Calendar.MINUTE));
        ws.set(Calendar.SECOND, ws.getMaximum(Calendar.SECOND));
        ws.set(Calendar.MILLISECOND, ws.getMaximum(Calendar.MILLISECOND));
        return ws;
    }

    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.increment_week:
            weekStart.add(Calendar.WEEK_OF_YEAR, 1);
            weekEnd.add(Calendar.WEEK_OF_YEAR, 1);
            break;
        case R.id.decrement_week:
            weekStart.add(Calendar.WEEK_OF_YEAR, -1);
            weekEnd.add(Calendar.WEEK_OF_YEAR, -1);
            break;
        default:
            break;
        }
        String beginning = TITLE_FORMAT.format(weekStart.getTime());
        String ending = TITLE_FORMAT.format(weekEnd.getTime());
        String title = getString(R.string.report_title, beginning, ending);
        setTitle( title );
        reportModel.setWeekStart(weekStart);
        reportModel.setWeekEnd(weekEnd);
        layoutView.fillInReport(reportModel.rows());
        weekView.setText(getString(R.string.week,WEEK_FORMAT.format(weekStart.getTime())));
    }
}