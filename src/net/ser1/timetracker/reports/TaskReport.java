package net.ser1.timetracker.reports;

import static net.ser1.timetracker.DBHelper.END;
import static net.ser1.timetracker.DBHelper.NAME;
import static net.ser1.timetracker.DBHelper.RANGES_TABLE;
import static net.ser1.timetracker.DBHelper.RANGE_COLUMNS;
import static net.ser1.timetracker.DBHelper.START;
import static net.ser1.timetracker.DBHelper.TASK_COLUMNS;
import static net.ser1.timetracker.DBHelper.TASK_ID;
import static net.ser1.timetracker.DBHelper.TASK_TABLE;

import java.util.Calendar;

import net.ser1.timetracker.Tasks;
import net.ser1.timetracker.TimeRange;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class TaskReport extends AbstractReport {
    public String[][] rows() {
        // Iterate over each task and set the day values, and accumulate the day 
        // and week totals
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(TASK_TABLE, TASK_COLUMNS, null, null, null, null, NAME);
        // The totals for all tasks for each day, plus one for the week total.
        int dayTotalRowIdx = c.getCount();
        String[][] taskTimes = new String[dayTotalRowIdx+1][8];
        loadRows(taskTimes, c);
        c.close();
        db.close();
        return taskTimes;
    }

    public void refreshRows(String[][] taskTimes) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(TASK_TABLE, TASK_COLUMNS, null, null, null, null, NAME);
        loadRows(taskTimes, c);
        c.close();
        db.close();
    }
    
    protected void loadRows(String[][] taskTimes, Cursor c) {
        int dayTotalRowIdx = c.getCount();
        int[] dayTotals = new int[8];
        int taskIdx = 0;
        if (c.moveToFirst()) {
            do {
                String tid_s = c.getString(0);
                // Fetch an array of times (per day) for the task
                long[] days = getDays(tid_s);
                int weekTotal = 0;
                // The total for this task, for the whole week
                for (int i = 0 ; i < 7; i++) {
                    dayTotals[i] += days[i];
                    weekTotal += days[i];
                    taskTimes[taskIdx][i] = Tasks.formatTotal(decimalTime, FORMAT, days[i]);
                }
                // Set the week total.  Since this value can be more than 24 hours,
                // we have to format it by hand:
                taskTimes[taskIdx][7] = Tasks.formatTotal(decimalTime, FORMAT, weekTotal);
                dayTotals[7] += weekTotal;
                taskIdx++;
            } while (c.moveToNext());
        }
        
        for (int i = 0; i < 8 ; i++) {
            taskTimes[dayTotalRowIdx][i] = Tasks.formatTotal(decimalTime, FORMAT, dayTotals[i]); 
        }
    }

    public String[] rowNames() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(TASK_TABLE, TASK_NAMES, null, null, null, null, NAME);
        String[] names = new String[c.getCount()+1];
        int nameIdx = 0;
        if (c.moveToFirst()) {
            do {
                names[nameIdx++] = c.getString(0);
            } while (c.moveToNext());
        }
        names[names.length-1] = "";
        c.close();
        db.close();
        return names;
    }
    
    /**
     * Fetch the times for a task within the currently set time range, by day.
     * 
     * @param tid_s The ID of the task for which to fetch times
     * @return An array containinging, in each cell, the sum of the times which
     *         fall within that day. Index 0 is the first day starting on the
     *         currently set week.  This uses TimeRange.overlap() to make sure
     *         that only time that actually falls on the day is included (even
     *         if a particular time entry spans days).
     * @see TimeRange.overlap()
     */
    private long[] getDays(String tid_s) {
        Calendar day = Calendar.getInstance();
        day.setFirstDayOfWeek( startDay );
        long days[] = {0,0,0,0,0,0,0};
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor r = db.query(RANGES_TABLE, RANGE_COLUMNS, TASK_ID+" = ? AND "
                +START+" < ? AND ( "+END+" > ? OR "+END+" ISNULL )",
                new String[] { tid_s, 
                               String.valueOf(weekEnd.getTimeInMillis()),
                               String.valueOf(weekStart.getTimeInMillis())},
                null,null,null);

        if (r.moveToFirst()) {
            do {
                long start = r.getLong(0);
                long end;
                if (r.isNull(1)) {
                    end = System.currentTimeMillis();
                } else {
                    end = r.getLong(1);
                }
                
                day.setTimeInMillis(end);
                
                int[] weekDays = new int[7];
                for (int i = 0; i < 7; i++) {
                    weekDays[i] = ((weekStart.getFirstDayOfWeek()-1+i)%7)+1;
                }

                // At this point, "day" must be set to the start time
                for (int i=0; i<7; i++) {
                    day.set(Calendar.DAY_OF_WEEK, weekDays[i]);
                    days[i] += TimeRange.overlap(day, start, end);
                }
                
            } while (r.moveToNext());
        } 
        r.close();
        db.close();
        return days;
    }

}
