package net.ser1.timetracker;

import static net.ser1.timetracker.DBHelper.END;
import static net.ser1.timetracker.DBHelper.NAME;
import static net.ser1.timetracker.DBHelper.RANGES_TABLE;
import static net.ser1.timetracker.DBHelper.RANGE_COLUMNS;
import static net.ser1.timetracker.DBHelper.START;
import static net.ser1.timetracker.DBHelper.TASK_COLUMNS;
import static net.ser1.timetracker.DBHelper.TASK_ID;
import static net.ser1.timetracker.DBHelper.TASK_TABLE;
import static net.ser1.timetracker.Tasks.START_DAY;
import static net.ser1.timetracker.TimeRange.NULL;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.method.SingleLineTransformationMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class TaskAdapter extends BaseAdapter {

    private DBHelper dbHelper;
    protected ArrayList<Task> tasks;
    private Context savedContext;
    protected long currentRangeStart;
    protected long currentRangeEnd;
    private SharedPreferences preferences;
    protected int fontSize = 16;
    protected boolean decimalFormat;

    public TaskAdapter(Context c) {
        savedContext = c;
        dbHelper = new DBHelper(c);
        dbHelper.getWritableDatabase();
        tasks = new ArrayList<Task>();
        preferences = c.getSharedPreferences( Tasks.TIMETRACKERPREF, Context.MODE_PRIVATE);
        updatePreferences();
    }

    public void updatePreferences() {
        fontSize = preferences.getInt(Tasks.FONTSIZE, 16);
        decimalFormat = preferences.getBoolean(Tasks.TIMEDISPLAY, false);
    }

    public void close() {
        dbHelper.close();
    }
    
    public Task getTask( int id ) {
        for (Task t : tasks) {
            if (t.getId() == id )
                return t;
        }
        return null;
    }

    /**
     * Loads all tasks.
     */
    public boolean loadTasks() {
        currentRangeStart = currentRangeEnd = -1;
        return loadTasks("", true);
    }

    /**
     * Load tasks that have times for a given day
     * @param day
     */
    public boolean loadTasks(Calendar day) {
        return loadTasks(day, (Calendar) day.clone());
    }

    /**
     * Load tasks that have times within a given date range
     * @param start
     * @param end
     */
    public boolean loadTasks(Calendar start, Calendar end) {
        String[] res = makeWhereClause(start, end);
        return loadTasks(res[0], res[1] == null ? false : true);
    }

    /**
     * Java doesn't understand tuples, so the return value
     * of this is a hack.
     * @param start
     * @param end
     * @return a String pair hack, where the second item is null
     * for false, and non-null for true
     */
    private String[] makeWhereClause(Calendar start, Calendar end) {
        start = (Calendar)start.clone();
        end = (Calendar)end.clone();
        String query = "AND " + START + " < %d AND " + END + " >= %d";
        Calendar today = Calendar.getInstance();
        today.setFirstDayOfWeek(preferences.getInt(START_DAY, 0) + 1);
        today.set(Calendar.HOUR_OF_DAY, 12);
        for (int field : new int[]{Calendar.HOUR_OF_DAY, Calendar.MINUTE,
                    Calendar.SECOND,
                    Calendar.MILLISECOND}) {
            for (Calendar d : new Calendar[]{today, start, end}) {
                d.set(field, d.getMinimum(field));
            }
        }
        end.add(Calendar.DAY_OF_MONTH, 1);
        currentRangeStart = start.getTimeInMillis();
        currentRangeEnd = end.getTimeInMillis();
        boolean loadCurrentTask = today.compareTo(start) != -1 &&
                today.compareTo(end) != 1;
        query = String.format(query, end.getTimeInMillis(), start.getTimeInMillis());
        return new String[]{query, loadCurrentTask ? query : null};
    }

    /**
     * Load tasks, given a filter.  This overwrites any currently
     * loaded tasks in the "tasks" data structure.
     * 
     * @param whereClause A SQL where clause limiting the range of dates to
     *        load.  This must be a clause against the ranges table.
     * @param loadCurrent Whether or not to include data for currently active
     *        tasks.
     */
    private boolean loadTasks(String whereClause, boolean loadCurrent) {
        tasks.clear();

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(TASK_TABLE, TASK_COLUMNS, null, null, null, null, null);

        Task t = null;
        if (c.moveToFirst()) {
            do {
                int tid = c.getInt(0);
                String[] tids = {String.valueOf(tid)};
                t = new Task(c.getString(1), tid);
                Cursor r = db.rawQuery("SELECT SUM(end) - SUM(start) AS total FROM " + RANGES_TABLE + " WHERE " + TASK_ID + " = ? AND end NOTNULL " + whereClause, tids);
                if (r.moveToFirst()) {
                    t.setCollapsed(r.getLong(0));
                }
                r.close();
                if (loadCurrent) {
                    r = db.query(RANGES_TABLE, RANGE_COLUMNS,
                            TASK_ID + " = ? AND end ISNULL",
                            tids, null, null, null);
                    if (r.moveToFirst()) {
                        t.setStartTime(r.getLong(0));
                    }
                    r.close();
                }
                tasks.add(t);
            } while (c.moveToNext());
        }
        c.close();
        db.close();
        Collections.sort(tasks);
        boolean running = findCurrentlyActive().hasNext();
        notifyDataSetChanged();
        return running;
    }

    /**
     * Don't forget to close the cursor!!
     * @return
     */
    protected Cursor getCurrentRange() {
        String[] res = {""};
        if (currentRangeStart != -1 && currentRangeEnd != -1) {
            Calendar start = Calendar.getInstance();
            start.setFirstDayOfWeek(preferences.getInt(START_DAY, 0) + 1);
            start.setTimeInMillis(currentRangeStart);
            Calendar end = Calendar.getInstance();
            end.setFirstDayOfWeek(preferences.getInt(START_DAY, 0) + 1);
            end.setTimeInMillis(currentRangeEnd);
            res = makeWhereClause(start, end);
        }
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor r = db.rawQuery("SELECT t.name, r.start, r.end, r.note " +
                " FROM " + TASK_TABLE + " t, " + RANGES_TABLE + " r " +
                " WHERE r." + TASK_ID + " = t.ROWID " + res[0] +
                " ORDER BY t.name, r.start ASC", null);
        return r;
    }

    public Iterator<Task> findCurrentlyActive() {
        return new Iterator<Task>() {
            Iterator<Task> iter = tasks.iterator();
            Task next = null;
            public boolean hasNext() {
                if (next != null) return true;
                while (iter.hasNext()) {
                    Task t = iter.next();
                    if (t.isRunning()) {
                        next = t;
                        return true;
                    }
                }
                return false;
            }
            public Task next() {
                if (hasNext()) {
                    Task t = next;
                    next = null;
                    return t;
                }
                throw new NoSuchElementException();
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }                
        };
    }

    protected Task addTask(String taskName) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(NAME, taskName);
        long id = db.insert(TASK_TABLE, NAME, values);
        Task t = new Task(taskName, (int) id);
        tasks.add(t);
        Collections.sort(tasks);
        notifyDataSetChanged();
        return t;
    }

    protected void updateTask(Task t) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(NAME, t.getTaskName());
        String id = String.valueOf(t.getId());
        String[] vals = {id};
        db.update(TASK_TABLE, values, "ROWID = ?", vals);

        if (t.getStartTime() != NULL) {
            values.clear();
            long startTime = t.getStartTime();
            values.put(START, startTime);
            vals = new String[]{id, String.valueOf(startTime)};
            if (t.getEndTime() != NULL) {
                values.put(END, t.getEndTime());
            }
            // If an update fails, then this is an insert
            if (db.update(RANGES_TABLE, values, TASK_ID + " = ? AND " + START + " = ?", vals) == 0) {
                values.put(TASK_ID, t.getId());
                db.insert(RANGES_TABLE, END, values);
            }
        }

        Collections.sort(tasks);
        notifyDataSetChanged();
    }

    public void deleteTask(Task t) {
        tasks.remove(t);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String[] id = {String.valueOf(t.getId())};
        db.delete(TASK_TABLE, "ROWID = ?", id);
        db.delete(RANGES_TABLE, TASK_ID + " = ?", id);
        notifyDataSetChanged();
    }

    public int getCount() {
        return tasks.size();
    }

    public Object getItem(int position) {
        return tasks.get(position);
    }

    public long getItemId(int position) {
        return position;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        TaskView view = null;
        if (convertView == null) {
            Object item = getItem(position);
            if (item != null) {
                view = new TaskView(savedContext, (Task) item);
            }
        } else {
            view = (TaskView) convertView;
            Object item = getItem(position);
            if (item != null) {
                view.setTask((Task) item);
            }
        }
        return view;
    }
    
    /**
     * The view for an individial task in the list.
     */
    private class TaskView extends LinearLayout {
        private int fontSize = 20;

        /**
         * The view of the task name displayed in the list
         */
        private TextView taskName;
        /**
         * The view of the total time of the task.
         */
        private TextView total;
        private ImageView checkMark;

        public TaskView(Context context, Task t) {
            super(context);
            setOrientation(LinearLayout.HORIZONTAL);
            setPadding(5, 10, 5, 10);

            taskName = new TextView(context);
            taskName.setTextSize(fontSize);
            taskName.setText(t.getTaskName());
            addView(taskName, new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.FILL_PARENT, 1f));

            checkMark = new ImageView(context);
            checkMark.setImageResource(R.drawable.ic_check_mark_dark);
            checkMark.setVisibility(View.INVISIBLE);
            addView(checkMark, new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.FILL_PARENT, 0f));

            total = new TextView(context);
            total.setTextSize(fontSize);
            total.setGravity(Gravity.RIGHT);
            total.setTransformationMethod(SingleLineTransformationMethod.getInstance());
            total.setText(Tasks.formatTotal(decimalFormat, t.getTotal()));
            addView(total, new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.FILL_PARENT, 0f));

            setGravity(Gravity.TOP);
            markupSelectedTask(t);
        }

        public void setTask(Task t) {
            taskName.setTextSize(fontSize);
            total.setTextSize(fontSize);
            taskName.setText(t.getTaskName());
            total.setText(Tasks.formatTotal(decimalFormat, t.getTotal()));
            markupSelectedTask(t);
        }

        private void markupSelectedTask(Task t) {
            if (t.isRunning()) {
                checkMark.setVisibility(View.VISIBLE);
            } else {
                checkMark.setVisibility(View.INVISIBLE);
            }
        }
    }

	public void setRangeNote(int id, long start, String note)
	{
		SQLiteDatabase db = dbHelper.getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put(TASK_ID, id);
		values.put(DBHelper.NOTE, note);
		String[] vals = { ""+start, "" + id };
		db.update(RANGES_TABLE, values, START + " = ? AND " + TASK_ID + " = ?", vals);

	}
}