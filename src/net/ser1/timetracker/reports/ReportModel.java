package net.ser1.timetracker.reports;

import java.util.Calendar;

import net.ser1.timetracker.DBHelper;

import android.content.Context;

public interface ReportModel {
    // These are initialization methods called by Report before the model is
    // used.
    public void setWeekStart( Calendar weekStart );    
    public void setWeekEnd( Calendar weekEnd );
    public void setDecimalTime( boolean tf );
    public void setStartDay( int startDay );
    public void setContext( Context context );
    public void setDbHelper( DBHelper dbHelper );

    // These methods return data used by the report
    public String[]   columnNames();
    public String[]   rowNames();
    /**
     * 
     * @return rows [ row ] [ col ]
     */
    public String[][] rows();
    public void       refreshRows( String[][] values );
}
