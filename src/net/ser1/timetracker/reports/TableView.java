package net.ser1.timetracker.reports;

import net.ser1.timetracker.R;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

public class TableView implements ReportView {
    private static final int PAD = 2;
    private static final int RPAD = 4;
    private static final int DKYELLOW = Color.argb(150, 100, 100, 0);
    private static final int DKDKYELLOW = Color.argb(100, 75, 75, 0);
    
    private Context context;
    private TableLayout theTable;
    private TextView[][] dateViews;
    private int nCols;
    private View theView;
    
    public TableView( Context context ) {
        this.context = context;

        LayoutInflater factory = LayoutInflater.from(context);
        theView = factory.inflate(R.layout.report, null);
        
        theTable = (TableLayout)theView.findViewById(R.id.report);
    }
    
    public View getView() {
        return theView;
    }
    
    public void fillInReport(String[][] values) {
        for (int i=0; i < values.length; i++) {
            for (int j=0; j < values[i].length; j++) {
                TextView day = dateViews[i][j];
                day.setText(values[i][j]);
            }
        }
    }


    public void createHeader( String[] columnNames ) {
        nCols = columnNames.length;
        TableRow row = new TableRow(context);
        theTable.addView(row, new TableLayout.LayoutParams());

        TextView blank = new TextView(context);
        blank.setText(context.getString(R.string.task));
        blank.setPadding(PAD,PAD,RPAD,PAD);
        blank.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        row.addView(blank, new TableRow.LayoutParams(0));
        
        TextView header = null;
        TableRow.LayoutParams params = new TableRow.LayoutParams();
        for (int i = 1; i < nCols; i++) {
            header  = new TextView(context);
            header.setText( columnNames[i]);
            header.setPadding(PAD,PAD,RPAD,PAD);
            header.setGravity(Gravity.CENTER_HORIZONTAL);
            header.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
            if (i % 2 == 1)  header.setBackgroundColor(Color.DKGRAY);
            row.addView(header,params);
        }
        
        header.setPadding(PAD,PAD,RPAD,PAD+2);
        header.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        header.setBackgroundColor(DKYELLOW);
    }

    public void createReport(String[] rowNames) {
        dateViews = new TextView[rowNames.length][nCols];
        TableRow.LayoutParams zeroParams = new TableRow.LayoutParams(0);
        TableRow.LayoutParams params = new TableRow.LayoutParams();
        for (int i=0; i<rowNames.length; i++) {
            TableRow row = new TableRow(context);
            theTable.addView(row, params);

            TextView rowName = new TextView(context);
            rowName.setText(rowNames[i]);
            rowName.setPadding(PAD, PAD, RPAD, PAD);
            row.addView(rowName, zeroParams);

            for (int j = 0; j < nCols; j++) {
                TextView dayTime = new TextView(context);
                dateViews[i][j] = dayTime;
                dayTime.setPadding(PAD, PAD, RPAD, PAD);
                if (j % 2 == 1)
                    dayTime.setBackgroundColor(Color.DKGRAY);
                row.addView(dayTime, params);
            }

            TextView total = dateViews[i][nCols-1];
            total.setTypeface(Typeface.SANS_SERIF, Typeface.ITALIC);
            total.setBackgroundColor(DKYELLOW);
        }
        // The last row is the totals
        int totalRowIdx = rowNames.length-1;
        dateViews[totalRowIdx][0].setPadding(PAD,PAD*2,RPAD,PAD);
        for (int i = 0; i < nCols; i++) {
            TextView dayTotal = dateViews[totalRowIdx][i];
            dayTotal.setPadding(PAD,PAD*2,RPAD,PAD);
            dayTotal.setTypeface(Typeface.SANS_SERIF, Typeface.ITALIC);
            if (i % 2 == 1) 
                dayTotal.setBackgroundColor(DKYELLOW);
            else
                dayTotal.setBackgroundColor(DKDKYELLOW);
        }
    }
}
