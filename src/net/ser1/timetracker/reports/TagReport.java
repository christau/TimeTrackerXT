/**
 * 
 */
package net.ser1.timetracker.reports;

import static net.ser1.timetracker.DBHelper.END;
import static net.ser1.timetracker.DBHelper.RANGE_COLUMNS;
import static net.ser1.timetracker.DBHelper.START;
import static net.ser1.timetracker.DBHelper.TASK_ID;

import java.util.Calendar;
import java.util.Set;

import net.ser1.timetracker.DBHelper;
import net.ser1.timetracker.R;
import net.ser1.timetracker.Tag;
import net.ser1.timetracker.TagHandler;
import net.ser1.timetracker.Task;
import net.ser1.timetracker.TaskAdapter;
import net.ser1.timetracker.Tasks;
import net.ser1.timetracker.TimeRange;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * @author ser
 * 
 */
public class TagReport extends AbstractReport {
    private TagHandler tagHandler;
    private TaskAdapter adapter;
    private DBHelper dbHelper;

    private static final String timeQuery = 
        TASK_ID+" = ? AND "+START+" <= ? AND "+END+" >= ?";
    private int getTimeFor(Task t, Calendar day) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Calendar startTime = (Calendar)day.clone();
        Calendar endTime = (Calendar)day.clone();
        startTime.set(Calendar.HOUR, 0);
        startTime.set(Calendar.MINUTE, 0);
        startTime.set(Calendar.SECOND, 0);
        startTime.set(Calendar.MILLISECOND, 0);
        endTime.set(Calendar.HOUR_OF_DAY, endTime.getMaximum(Calendar.HOUR_OF_DAY));
        endTime.set(Calendar.MINUTE, endTime.getMaximum(Calendar.MINUTE));
        endTime.set(Calendar.SECOND, endTime.getMaximum(Calendar.SECOND));
        endTime.set(Calendar.MILLISECOND, endTime.getMaximum(Calendar.MILLISECOND));
        long ms_start = startTime.getTimeInMillis();
        long ms_end = endTime.getTimeInMillis();

        String[] selectionArgs = { String.valueOf(t.getId()), 
                String.valueOf(ms_end),      // column 1
                String.valueOf(ms_start)};   // column 0
        Cursor cursor = db.query(DBHelper.RANGES_TABLE, RANGE_COLUMNS, 
                timeQuery, selectionArgs, null, null, null);
        int sum = 0;
        if (cursor.moveToFirst()) {
            do {
                sum += TimeRange.overlap(ms_start, ms_end, cursor.getLong(0), cursor.getLong(1));        
            } while (cursor.moveToNext());
        }
        
        cursor.close();
        db.close();
        return sum;
    }

    public String[][] rows() {
        adapter.loadTasks(weekStart, weekEnd);
        Set<Tag> tags = tagHandler.getTags();
        String[][] tagTimes = new String[tags.size() + 1][8];
        refreshRows(tagTimes);
        return tagTimes;
    }

    public void refreshRows(String[][] tagTimes) {
        Set<Tag> tags = tagHandler.getTags();
        int[] totalTimeForDay = new int[8];
        int tid = 0, did = 0;
        for (Tag tag : tags) {
            int totalTimeForTag = 0;
            int[] taskIds = tag.getTasks();
            did = 0;
            for (Calendar day = (Calendar)weekStart.clone(); 
                 day.before(weekEnd); 
                 day.add(Calendar.DAY_OF_MONTH, 1)) {
                int allTaskTimesForDay = 0;
                for (int taskId : taskIds) {
                    Task t = adapter.getTask(taskId);
                    allTaskTimesForDay += getTimeFor( t, day );
                }
                totalTimeForTag += allTaskTimesForDay;
                totalTimeForDay[did] += allTaskTimesForDay;
                tagTimes[tid][did] = Tasks.formatTotal(decimalTime, FORMAT, allTaskTimesForDay);
                did++;
            }
            totalTimeForDay[did] += totalTimeForTag;
            tagTimes[tid][did] = Tasks.formatTotal(decimalTime, FORMAT, totalTimeForTag);
            tid++;
        }
        did = 0;
        for (int dayTotal : totalTimeForDay) {
            tagTimes[tid][did++] = Tasks.formatTotal(decimalTime, FORMAT, dayTotal);
        }
    }

    
    @Override
    public void setContext(Context c) {
        if (adapter != null)
            adapter.close();
        adapter = new TaskAdapter(c);
        tagHandler = new TagHandler(c);
        dbHelper = new DBHelper(c);
        super.setContext(c);
    }

    @Override
    public String[] rowNames() {
        String[] tagNames = tagHandler.getTagNames();
        String[] rowNames = new String[tagNames.length + 1];
        System.arraycopy(tagNames, 0, rowNames, 0, tagNames.length);
        rowNames[rowNames.length - 1] = "";
        return rowNames;
    }
    
    @Override
    public String[] columnNames() {
        String[] columnNames = super.columnNames();
        columnNames[0] = context.getString(R.string.tag_name);
        return columnNames;
    }
}
