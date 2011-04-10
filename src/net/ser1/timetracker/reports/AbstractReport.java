package net.ser1.timetracker.reports;

import static net.ser1.timetracker.DBHelper.NAME;

import java.util.Calendar;

import net.ser1.timetracker.DBHelper;
import net.ser1.timetracker.R;
import android.content.Context;

public abstract class AbstractReport implements ReportModel {
    protected static final String[] TASK_NAMES = new String[] {NAME};
    protected DBHelper dbHelper;
    protected Calendar weekStart;
    protected Calendar weekEnd;
    protected Context context;
    protected boolean decimalTime = false;
    protected int startDay;
    protected static final String FORMAT = "%02d:%02d";

    /**
     * Fetch a list of the column names, including the first (the row name)
     */
    public String[] columnNames() {
        String[] columnTitles = new String[9];
        columnTitles[0] = context.getString(R.string.task_name);
        columnTitles[8] = context.getString(R.string.total);

        Calendar calRef = Calendar.getInstance();
        int weekDay;
        
        for (int i = 0; i < 7; i++) {
            weekDay = ((weekStart.getFirstDayOfWeek()-1+i)%7)+1;
            calRef.set(Calendar.DAY_OF_WEEK, weekDay);
            columnTitles[i+1] = String.format("%1$ta", calRef);
        }

        return columnTitles;
    }

    public abstract void refreshRows(String[][] values);

    /**
     * Fetch a list of all row names, excluding the header row
     */
    public abstract String[] rowNames();

    /**
     * Fetch the values of the table, excluding the row name and the column 
     * header row.
     */
    public abstract String[][] rows();


    public void setContext(Context context) {
        this.context = context;
    }

    public void setDbHelper(DBHelper dbHelper) {
        this.dbHelper = dbHelper;
    }
    
    public void setDecimalTime(boolean tf) {
        this.decimalTime = tf;
    }

    public void setStartDay(int startDay) {
        this.startDay = startDay;
    }
    
    public void setWeekStart( Calendar weekStart ) {
        this.weekStart = (Calendar)weekStart.clone();
    }
    
    public void setWeekEnd( Calendar weekEnd ) {
        this.weekEnd = (Calendar)weekEnd.clone();
    }

}
