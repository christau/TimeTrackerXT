package net.ser1.timetracker.reports;

import android.view.View;

public interface ReportView {
    public View getView();
    
    public void createHeader( String[] columnNames );
    public void createReport( String[] rowNames );
    
    public void fillInReport( String[][] values );
}
