/**
 * TimeTracker 
 * ©2008, 2009 Sean Russell
 * @author Sean Russell <ser@germane-software.com>
 */
package net.ser1.timetracker;

import static net.ser1.timetracker.DBHelper.END;
import static net.ser1.timetracker.DBHelper.START;
import static net.ser1.timetracker.DBHelper.TASK_ID;
import static net.ser1.timetracker.Report.weekEnd;
import static net.ser1.timetracker.Report.weekStart;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.text.util.Linkify;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;

/**
 * Manages and displays a list of tasks, providing the ability to edit and
 * display individual task items.
 * @author ser
 */
public class Tasks extends ListActivity {
    private static final String EDIT_TASK_BUNDLE_KEY = "edit_task";
    public static final String TIMETRACKERPREF = "timetracker.pref";
    protected static final String FONTSIZE = "font-size";
    protected static final String MILITARY = "military-time";
    protected static final String CONCURRENT = "concurrent-tasks";
    protected static final String SOUND = "sound-enabled";
    protected static final String VIBRATE = "vibrate-enabled";

    protected static final String START_DAY = "start_day";
    protected static final String START_DATE = "start_date";
    protected static final String END_DATE = "end_date";
    protected static final String VIEW_MODE = "view_mode";
    protected static final String REPORT_DATE = "report_date";
	protected static final String TIMEDISPLAY = "time_display";
    public enum Result { SUCCESS, FAILURE };
	
    /**
     * Defines how each task's time is displayed 
     */
    private static final String FORMAT = "%02d:%02d";
    private static final String DECIMAL_FORMAT = "%02d"+
        (new DecimalFormatSymbols().getDecimalSeparator())+"%02d";
    /**
     * How often to refresh the display, in milliseconds
     */
    private static final int REFRESH_MS = 60000;
    /**
     * The model for this view
     */
    private TaskAdapter adapter;
    /**
     * A timer for refreshing the display.
     */
    private Handler timer;
    /**
     * The call-back that actually updates the display.
     */
    private TimerTask updater;
    /**
     * The currently active task (the one that is currently being timed).  There
     * can be only one.
     */
    private boolean running = false;
    /**
     * The currently selected task when the context menu is invoked.
     */
    private Task selectedTask;
    /**
     * Handles the DB storage of tags
     */
    private TagHandler tagHandler;
    /**
     * A list of known tags
     */
    private Set<Tag> tags;
    private SharedPreferences preferences;
    private boolean concurrency;
    private static MediaPlayer clickPlayer;
    private boolean playClick = false;
    private boolean vibrateClick = true;
    private Vibrator vibrateAgent;
    private ProgressDialog progressDialog = null;
    private boolean decimalFormat = false;
    
    /**
     * A list of menu options, including both context and options menu items 
     */
    protected static final int ADD_TASK = 0,
            EDIT_TASK = 1,  DELETE_TASK = 2,  REPORT = 3,  SHOW_TIMES = 4,
            CHANGE_VIEW = 5,  SELECT_START_DATE = 6,  SELECT_END_DATE = 7,
            HELP = 8,  EXPORT_VIEW = 9,  SUCCESS_DIALOG = 10,  ERROR_DIALOG = 11,
            SET_WEEK_START_DAY = 12,  MORE = 13,  BACKUP = 14, PREFERENCES = 15,
            PROGRESS_DIALOG = 16;
    protected static final int BACKUP_DB = 0, RESTORE_DB = 1;
        // TODO: This could be done better...
    private static final String dbPath = "/data/data/net.ser1.timetracker/databases/timetracker.db";
    private static final String dbBackup = "/sdcard/timetracker.db";



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences( TIMETRACKERPREF,MODE_PRIVATE);
        concurrency = preferences.getBoolean(CONCURRENT, false);
        if (preferences.getBoolean(MILITARY, true)) {
            TimeRange.FORMAT = new SimpleDateFormat("HH:mm");
        } else {
            TimeRange.FORMAT = new SimpleDateFormat("hh:mm a");
        }

        int which = preferences.getInt(VIEW_MODE, 0);
        if (adapter == null) {
            adapter = new TaskAdapter(this);
            setListAdapter(adapter);
            switchView(which);
        }
        if (timer == null) {
            timer = new Handler();
        }
        if (updater == null) {
            updater = new TimerTask() {

                @Override
                public void run() {
                    if (running) {
                        adapter.notifyDataSetChanged();
                        setTitle();
                        Tasks.this.getListView().invalidate();
                    }
                    timer.postDelayed(this, REFRESH_MS);
                }
            };
        }
        playClick = preferences.getBoolean(SOUND, false);
        if (playClick && clickPlayer == null) {
            clickPlayer = MediaPlayer.create(this, R.raw.click);
            try {
                clickPlayer.prepareAsync();
            } catch (IllegalStateException illegalStateException) {
                // ignore this.  There's nothing the user can do about it.
                Logger.getLogger("TimeTracker").log(Level.SEVERE,
                        "Failed to set up audio player: "
                        +illegalStateException.getMessage());
            }
        }
        decimalFormat = preferences.getBoolean(TIMEDISPLAY, false);
        registerForContextMenu(getListView());
        if (adapter.tasks.size() == 0) {
            showDialog(HELP);
        }
        vibrateAgent = (Vibrator)getSystemService(VIBRATOR_SERVICE);
        vibrateClick = preferences.getBoolean(VIBRATE, true);

        tagHandler = new TagHandler(this);
        tags = tagHandler.getTags();
                
        if (savedInstanceState != null && savedInstanceState.containsKey(EDIT_TASK_BUNDLE_KEY)) {
            selectedTask = adapter.getTask( savedInstanceState.getInt( EDIT_TASK_BUNDLE_KEY ));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (timer != null) {
            timer.removeCallbacks(updater);
        }
    }

    @Override
    protected void onStop() {
        if (adapter != null)
            adapter.close();
        if (clickPlayer != null)
            clickPlayer.release();
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // This is only to cause the view to reload, so that we catch 
        // updates to the time list.
        int which = preferences.getInt(VIEW_MODE, 0);
        switchView(which);

        if (timer != null && running) {
            timer.post(updater);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, ADD_TASK, 0, R.string.add_task_title).setIcon(android.R.drawable.ic_menu_add);
        menu.add(0, REPORT, 1, R.string.generate_report_title).setIcon(android.R.drawable.ic_menu_week);
        menu.add(0, MORE, 2, R.string.more).setIcon(android.R.drawable.ic_menu_more);
        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        menu.setHeaderTitle(getText(R.string.task_menu));
        menu.add(0, EDIT_TASK, 0, getText(R.string.edit_task));
        menu.add(0, DELETE_TASK, 0, getText(R.string.delete_task));
        menu.add(0, SHOW_TIMES, 0, getText(R.string.show_times));
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        selectedTask = (Task) adapter.getItem((int) info.id);
        switch (item.getItemId()) {
            case SHOW_TIMES:
                Intent intent = new Intent(this, TaskTimes.class);
                intent.putExtra(TASK_ID, selectedTask.getId());
                if (adapter.currentRangeStart != -1) {
                    intent.putExtra(START, adapter.currentRangeStart);
                    intent.putExtra(END, adapter.currentRangeEnd);
                }
                startActivity(intent);
                break;
            case EDIT_TASK:
                startEditTask();
                break;
            default:
                showDialog(item.getItemId());
                break;
        }
        return super.onContextItemSelected(item);
    }
    
    private void startEditTask() {
        Intent intent1 = new Intent(this, EditTask.class);
        if (selectedTask == null) {
            intent1.putExtra(EditTask.TASK_ID, -1);
            intent1.putExtra(EditTask.TASK_NAME, "");
        } else {
            intent1.putExtra(EditTask.TASK_ID, selectedTask.getId());
            intent1.putExtra(EditTask.TASK_NAME, selectedTask.getTaskName());
        }
        intent1.putExtra(EditTask.TAGS, (Serializable) tags);
        startActivityForResult(intent1,EDIT_TASK);
    }

    
    private AlertDialog operationSucceed;
    private AlertDialog operationFailed;

    private String exportMessage;
    private String baseTitle;

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch (item.getItemId()) {
            case ADD_TASK:
                selectedTask = null;
                startEditTask();
                break;
            case MORE:
                showDialog(item.getItemId());
                break;
            case REPORT:
                Intent intent = new Intent(this, Report.class);
                intent.putExtra(REPORT_DATE, System.currentTimeMillis());
                intent.putExtra(START_DAY, preferences.getInt(START_DAY, 0) + 1);
                intent.putExtra(TIMEDISPLAY, decimalFormat);
                startActivity(intent);
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
            case DELETE_TASK:
                return openDeleteTaskDialog();
            case CHANGE_VIEW:
                return openChangeViewDialog();
            case HELP:
                return openAboutDialog();
            case SUCCESS_DIALOG:
                operationSucceed = new AlertDialog.Builder(Tasks.this)
                    .setTitle(R.string.success)
                    .setIcon(android.R.drawable.stat_notify_sdcard)
                    .setMessage(exportMessage)
                    .setPositiveButton(android.R.string.ok, null)
                    .create();
                return operationSucceed;
            case ERROR_DIALOG:
                operationFailed = new AlertDialog.Builder(Tasks.this)
                        .setTitle(R.string.failure)
                        .setIcon(android.R.drawable.stat_notify_sdcard)
                        .setMessage(exportMessage)
                        .setPositiveButton(android.R.string.ok, null)
                        .create();
                return operationFailed;
            case PROGRESS_DIALOG:
                progressDialog = new ProgressDialog(this);
                progressDialog.setMessage(getString(R.string.copying_records));
                progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                progressDialog.setCancelable(false);
                return progressDialog;
            case MORE:
                return new AlertDialog.Builder(Tasks.this).setItems(R.array.moreMenu, new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        DBBackup backup;
                        switch (which) {
                            case 0: // CHANGE_VIEW:
                                showDialog(CHANGE_VIEW);
                                break;
                            case 1: // EXPORT_VIEW:
                                File fout = getExportCsvFileName(); 
                                Result result = export( fout );
                                perform(result, fout.getName(), 
                                        R.string.export_csv_success, 
                                        R.string.export_csv_fail);
                                break;
                            case 2: // COPY DB TO SD
                                showDialog(Tasks.PROGRESS_DIALOG);
                                if (new File(dbBackup).exists()) {
                                    // Find the database
                                    SQLiteDatabase backupDb = SQLiteDatabase.openDatabase(dbBackup, null, SQLiteDatabase.OPEN_READWRITE);
                                    SQLiteDatabase appDb = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY);
                                    backup = new DBBackup(Tasks.this, progressDialog, BACKUP_DB);
                                    backup.execute(appDb, backupDb);
                                } else {
                                    InputStream in = null;
                                    OutputStream out = null;

                                    try {
                                        in = new BufferedInputStream(new FileInputStream(dbPath));
                                        out = new BufferedOutputStream(new FileOutputStream(dbBackup));
                                        for (int c = in.read(); c != -1; c = in.read()) {
                                            out.write(c);
                                        }
                                    } catch (Exception ex) {
                                        Logger.getLogger(Tasks.class.getName()).log(Level.SEVERE, null, ex);
                                        exportMessage = ex.getLocalizedMessage();
                                        showDialog(ERROR_DIALOG);
                                    } finally {
                                        try { in.close(); } catch (IOException ioe) { }
                                        try { out.close(); } catch (IOException ioe) { }
                                    }
                                }
                                break;
                            case 3: // RESTORE FROM BACKUP
                                showDialog(Tasks.PROGRESS_DIALOG);
                                SQLiteDatabase backupDb = SQLiteDatabase.openDatabase(dbBackup, null, SQLiteDatabase.OPEN_READONLY);
                                SQLiteDatabase appDb = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READWRITE);
                                backup = new DBBackup(Tasks.this, progressDialog, RESTORE_DB);
                                backup.execute(backupDb, appDb);
                                break;
                            case 4: // PREFERENCES
                                Intent intent = new Intent(Tasks.this, Preferences.class);
                                startActivityForResult(intent,PREFERENCES);
                                break;
                            case 5: // HELP:
                                showDialog(HELP);
                                break;
                            default:
                                break;
                        }
                    }

                }).create();
        }
        return null;
    }

    protected void perform(Result result, String message, int successId, int failureId) {
        switch (result) {
        case SUCCESS:
            exportMessage = getString(successId, message);
            if (operationSucceed != null) {
                operationSucceed.setMessage(exportMessage);
            }
            showDialog(SUCCESS_DIALOG);
            break;
        case FAILURE:
            exportMessage = getString(failureId, message);
            if (operationFailed != null) {
                operationFailed.setMessage(exportMessage);
            }
            showDialog(ERROR_DIALOG);
            break;
        }
    }

    /**
     * Creates a progressDialog to change the dates for which task times are shown.
     * Offers a short selection of pre-defined defaults, and the option to
     * choose a range from a progressDialog.
     * 
     * @see arrays.xml
     * @return the progressDialog to be displayed
     */
    private Dialog openChangeViewDialog() {
        return new AlertDialog.Builder(Tasks.this).setItems(R.array.views, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                SharedPreferences.Editor ed = preferences.edit();
                ed.putInt(VIEW_MODE, which);
                ed.commit();
                if (which == 5) {
                    Calendar calInstance = Calendar.getInstance();
                    new DatePickerDialog(Tasks.this,
                            new DatePickerDialog.OnDateSetListener() {

                                public void onDateSet(DatePicker view, int year,
                                        int monthOfYear, int dayOfMonth) {
                                    Calendar start = Calendar.getInstance();
                                    start.set(Calendar.YEAR, year);
                                    start.set(Calendar.MONTH, monthOfYear);
                                    start.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                                    start.set(Calendar.HOUR, start.getMinimum(Calendar.HOUR));
                                    start.set(Calendar.MINUTE, start.getMinimum(Calendar.MINUTE));
                                    start.set(Calendar.SECOND, start.getMinimum(Calendar.SECOND));
                                    start.set(Calendar.MILLISECOND, start.getMinimum(Calendar.MILLISECOND));
                                    SharedPreferences.Editor ed = preferences.edit();
                                    ed.putLong(START_DATE, start.getTime().getTime());
                                    ed.commit();

                                    new DatePickerDialog(Tasks.this,
                                            new DatePickerDialog.OnDateSetListener() {

                                                public void onDateSet(DatePicker view, int year,
                                                        int monthOfYear, int dayOfMonth) {
                                                    Calendar end = Calendar.getInstance();
                                                    end.set(Calendar.YEAR, year);
                                                    end.set(Calendar.MONTH, monthOfYear);
                                                    end.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                                                    end.set(Calendar.HOUR, end.getMaximum(Calendar.HOUR));
                                                    end.set(Calendar.MINUTE, end.getMaximum(Calendar.MINUTE));
                                                    end.set(Calendar.SECOND, end.getMaximum(Calendar.SECOND));
                                                    end.set(Calendar.MILLISECOND, end.getMaximum(Calendar.MILLISECOND));
                                                    SharedPreferences.Editor ed = preferences.edit();
                                                    ed.putLong(END_DATE, end.getTime().getTime());
                                                    ed.commit();
                                                    Tasks.this.switchView(5);  // Update the list view
                                                }
                                            },
                                            year,
                                            monthOfYear,
                                            dayOfMonth).show();
                                }
                            },
                            calInstance.get(Calendar.YEAR),
                            calInstance.get(Calendar.MONTH),
                            calInstance.get(Calendar.DAY_OF_MONTH)).show();
                } else {
                    switchView(which);
                }
            }
        }).create();
    }

    private void switchView(int which) {
        Calendar tw = Calendar.getInstance();
        int startDay = preferences.getInt(START_DAY, 0) + 1;
        tw.setFirstDayOfWeek(startDay);
        String ttl = getString(R.string.title,
                getResources().getStringArray(R.array.views)[which]);
        switch (which) {
            case 0: // today
                running = adapter.loadTasks(tw);
                break;
            case 1: // this week
                running = adapter.loadTasks(weekStart(tw, startDay), weekEnd(tw, startDay));
                break;
            case 2: // yesterday
                tw.add(Calendar.DAY_OF_MONTH, -1);
                running = adapter.loadTasks(tw);
                break;
            case 3: // last week
                tw.add(Calendar.WEEK_OF_YEAR, -1);
                running = adapter.loadTasks(weekStart(tw, startDay), weekEnd(tw, startDay));
                break;
            case 4: // all
                running = adapter.loadTasks();
                break;
            case 5: // select range
                Calendar start = Calendar.getInstance();
                start.setTimeInMillis(preferences.getLong(START_DATE, 0));
                Calendar end = Calendar.getInstance();
                end.setTimeInMillis(preferences.getLong(END_DATE, 0));
                running = adapter.loadTasks(start, end);
                DateFormat f = DateFormat.getDateInstance(DateFormat.SHORT);
                ttl = getString(R.string.title,
                        f.format(start.getTime()) + " - " + f.format(end.getTime()));
                break;
            default: // Unknown
                break;
        }
        baseTitle = ttl;
        setTitle();
        getListView().invalidate();
    }
    
    private void setTitle() {
        long total = 0;
        for (Task t : adapter.tasks) {
            total += t.getTotal();
        }
        setTitle(baseTitle + " " + formatTotal(decimalFormat, total));
    }

    public void addTask( String name ) {
        adapter.addTask(name);
    }

    @Override
    protected void onSaveInstanceState( Bundle bundle ) {
        if (selectedTask != null) {
            bundle.putInt(EDIT_TASK_BUNDLE_KEY, selectedTask.getId());
        }
    }

    /**
     * Constructs a progressDialog asking for confirmation for a delete request.  If
     * accepted, deletes the task.  If cancelled, closes the progressDialog.
     * @return the progressDialog to display
     */
    private Dialog openDeleteTaskDialog() {
        if (selectedTask == null) {
            return null;
        }
        String formattedMessage = getString(R.string.delete_task_message,
                selectedTask.getTaskName());
        return new AlertDialog.Builder(Tasks.this).setTitle(R.string.delete_task_title).setIcon(android.R.drawable.stat_sys_warning).setCancelable(true).setMessage(formattedMessage).setPositiveButton(R.string.delete_ok, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int whichButton) {
                adapter.deleteTask(selectedTask);
                Tasks.this.getListView().invalidate();
            }
        }).setNegativeButton(android.R.string.cancel, null).create();
    }
    final static String SDCARD = "/sdcard/";

    private File getExportCsvFileName() {
        String rangeName = getRangeName();
        String fname = rangeName + ".csv";
        // Change the file name until there's no conflict
        int counter = 0;
        File fout = new File(SDCARD + fname);
        while (fout.exists()) {
            fname = rangeName + "_" + counter + ".csv";
            fout = new File(SDCARD + fname);
            counter++;
        }
        return fout;
    }
    
    private Result export(File fout) {
        // FIXME: This really should be threaded, with a progress dialog
        // Export, then show a result dialog
        try {
            OutputStream out = new FileOutputStream(fout);
            Cursor currentRange = adapter.getCurrentRange();
            CSVExporter.exportRows(out, currentRange);
            currentRange.close();

            return Result.SUCCESS;
        } catch (FileNotFoundException fnfe) {
            fnfe.printStackTrace(System.err);
            return Result.FAILURE;
        }
    }

    private String getRangeName() {
        if (adapter.currentRangeStart == -1) {
            return "all";
        }
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd");
        Date d = new Date();
        d.setTime(adapter.currentRangeStart);
        return f.format(d);
    }

    private Dialog openAboutDialog() {
        String versionName = "";
        try {
            PackageInfo pkginfo = this.getPackageManager().getPackageInfo("net.ser1.timetracker", 0);
            versionName = pkginfo.versionName;
        } catch (NameNotFoundException nnfe) {
            // Denada
        }

        String formattedVersion = getString(R.string.version, versionName);

        LayoutInflater factory = LayoutInflater.from(this);
        View about = factory.inflate(R.layout.about, null);

        TextView version = (TextView) about.findViewById(R.id.version);
        version.setText(formattedVersion);
        TextView donate = (TextView) about.findViewById(R.id.donate);
        donate.setClickable(true);
        donate.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.germane-software.com/donate.html"));
                startActivity(intent);
            }
        });
        TextView links = (TextView) about.findViewById(R.id.usage);
        Linkify.addLinks(links, Linkify.ALL);
        links = (TextView) about.findViewById(R.id.credits);
        Linkify.addLinks(links, Linkify.ALL);

        return new AlertDialog.Builder(Tasks.this).setView(about).setPositiveButton(android.R.string.ok, null).create();
    }

    @Override
    protected void onPrepareDialog(int id, Dialog d) {
        EditText textView;
        switch (id) {
            case ADD_TASK:
                textView = (EditText) d.findViewById(R.id.task_edit_name_edit);
                textView.setText("");
                break;
            case EDIT_TASK:
                textView = (EditText) d.findViewById(R.id.task_edit_name_edit);
                textView.setText(selectedTask.getTaskName());
                break;
            default:
                break;
        }
    }

    private static final long MS_H = 3600000;
    private static final long MS_M = 60000;
    private static final long MS_S = 1000;
    private static final double D_M = 10.0 / 6.0;
    private static final double D_S = 1.0 / 36.0;

    /*
     * This is pretty stupid, but because Java doesn't support closures, we have
     * to add extra overhead (more method indirection; method calls are relatively 
     * expensive) if we want to re-use code.  Notice that a call to this method
     * actually filters down through four methods before it returns.
     */
    static String formatTotal( boolean decimalFormat, long ttl ) {
    	return formatTotal( decimalFormat, FORMAT, ttl );
    }
    public static String formatTotal( boolean decimalFormat, String format, long ttl ) {
        long hours = ttl / MS_H;
        long hours_in_ms = hours * MS_H;
        long minutes = (ttl - hours_in_ms) / MS_M;
    	long minutes_in_ms = minutes * MS_M;
        long seconds = (ttl - hours_in_ms - minutes_in_ms) / MS_S;
        return formatTotal( decimalFormat, format, hours, minutes, seconds );    	
    }
    static String formatTotal( boolean decimalFormat, long hours, long minutes, long seconds ) {
    	return formatTotal(decimalFormat,FORMAT,hours,minutes,seconds);
    }
    static String formatTotal( boolean decimalFormat, String format, long hours, long minutes, long seconds ) {
        if (decimalFormat) {
        	format = DECIMAL_FORMAT;
        	minutes = Math.round((D_M * minutes) + (D_S * seconds));
        	seconds = 0;
        }
        return String.format(format, hours, minutes, seconds);            	    	
    }

    @SuppressWarnings("unchecked")
    public List<Task> getTasks() {
        return (List<Task>)adapter.tasks.clone();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        if (vibrateClick) vibrateAgent.vibrate(100);
        if (playClick) {
            try {
                //clickPlayer.prepare();
                clickPlayer.start();
            } catch (Exception exception) {
                // Ignore this; it is probably because the media isn't yet ready.
                // There's nothing the user can do about it.
                // ignore this.  There's nothing the user can do about it.
                Logger.getLogger("TimeTracker").log(Level.INFO,
                        "Failed to play audio: "
                        +exception.getMessage());
            }
        }

        // Stop the update.  If a task is already running and we're stopping
        // the timer, it'll stay stopped.  If a task is already running and 
        // we're switching to a new task, or if nothing is running and we're
        // starting a new timer, then it'll be restarted.

        Object item = getListView().getItemAtPosition(position);
        if (item != null) {
            Task selected = (Task) item;
            if (!concurrency) {
                boolean startSelected = !selected.isRunning();
                if (running) {
                    running = false;
                    timer.removeCallbacks(updater);
                    // Disable currently running tasks
                    for (Iterator<Task> iter = adapter.findCurrentlyActive();
                         iter.hasNext();) {
                        Task t = iter.next();
                        t.stop();
                        adapter.updateTask(t);
                    }
                }
                if (startSelected) {
                    selected.start();
                    running = true;
                    timer.post(updater);
                }
            } else {
                if (selected.isRunning()) {
                    selected.stop();
                    running = adapter.findCurrentlyActive().hasNext();
                    if (!running) timer.removeCallbacks(updater);
                } else {
                    selected.start();
                    if (!running) {
                        running = true;
                        timer.post(updater);
                    }
                }
            }
            adapter.updateTask(selected);
        }
        getListView().invalidate();
        super.onListItemClick(l, v, position, id);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data == null) return;
        Bundle extras = data.getExtras();
        if (requestCode == PREFERENCES && resultCode == Activity.RESULT_OK) {
            if (extras.getBoolean(START_DAY)) {
                switchView(preferences.getInt(VIEW_MODE, 0));
            }
            if (extras.getBoolean(MILITARY)) {
                if (preferences.getBoolean(MILITARY, true)) {
                    TimeRange.FORMAT = new SimpleDateFormat("HH:mm");
                } else {
                    TimeRange.FORMAT = new SimpleDateFormat("hh:mm a");
                }
            }
            if (extras.getBoolean(CONCURRENT)) {
                concurrency = preferences.getBoolean(CONCURRENT, false);
            }
            if (extras.getBoolean(SOUND)) {
                playClick = preferences.getBoolean(SOUND, false);
                if (playClick && clickPlayer == null) {
                    clickPlayer = MediaPlayer.create(this, R.raw.click);
                    try {
                        clickPlayer.prepareAsync();
                        clickPlayer.setVolume(1, 1);
                    } catch (IllegalStateException illegalStateException) {
                        // ignore this.  There's nothing the user can do about it.
                        Logger.getLogger("TimeTracker").log(Level.SEVERE,
                                "Failed to set up audio player: "
                                +illegalStateException.getMessage());
                    }
                }
            }
            if (extras.getBoolean(VIBRATE)) {
                vibrateClick = preferences.getBoolean(VIBRATE, true);
            }
            if (extras.getBoolean(TIMEDISPLAY)) {
            	decimalFormat = preferences.getBoolean(TIMEDISPLAY, false);
            }
            adapter.updatePreferences();
        } else if (requestCode == EDIT_TASK && resultCode == Activity.RESULT_OK) {
            Set<Tag> newTags = (Set<Tag>)extras.getSerializable(EditTask.TAGS);
            int taskId = extras.getInt(EditTask.TASK_ID);
            String taskName = extras.getString(EditTask.TASK_NAME);

            Task task;
            if (taskId == -1) {
                task = adapter.addTask(taskName);
            } else {
                task = adapter.getTask(taskId);
                task.setTaskName(taskName);
                adapter.updateTask(selectedTask);
            }
            Tasks.this.getListView().invalidate();

            List<Tag> oldTags = tagHandler.getTags(taskId);
            for (Iterator<Tag> iter = newTags.iterator(); iter.hasNext(); ) {
                Tag tag = iter.next();
                tags.add(tag);
                if (oldTags.remove(tag)) {
                    iter.remove();
                }
            }
            for (Tag tag : oldTags) {
                tagHandler.untag(task, tag);
            }
            for (Tag tag : newTags) {
                tagHandler.tag(task, tag);
            }
        }

        if (getListView() != null) {
            getListView().invalidate();
        }
    }

    protected void finishedCopy( Result result, String message, int operation ) {
        if (result == Result.SUCCESS) {
            switchView(preferences.getInt(VIEW_MODE, 0));
            message = dbBackup;
        }
        int successId = R.string.backup_success, 
            failureId = R.string.backup_failed;
        if (operation == RESTORE_DB) {
            successId = R.string.restore_success;
            failureId = R.string.restore_failed;
        }
        perform(result, message, successId, failureId);
    }
}
