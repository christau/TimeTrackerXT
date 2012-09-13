package net.ser1.timetracker;

import java.io.OutputStream;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import android.database.Cursor;

public class CSVExporter {
    private static String escape( String s ) {
        if (s == null) return "\" \"";
            s = s.replaceAll("\"", "\"\"");
            s = s.replace("\r\n", " ").replace("\n", " ");
            if(s.length()==0)
        	    s=" ";
            s = "\"" + s + "\"";
        return s;
    }
    
    
    public static void exportRows( OutputStream o, String[][] rows ) {
        PrintStream outputStream = new PrintStream(o);
        for (String[] cols : rows) {
            String prepend = "";
            for (String col : cols) {
                outputStream.print(prepend);
                outputStream.print(escape(col));
                prepend = ",";                
            }
            outputStream.println();
        }    
    }
    
    
    public static void exportRows( OutputStream o, Cursor c ) {
        PrintStream outputStream = new PrintStream(o);
        String prepend = "";
        String[] columnNames = c.getColumnNames();
        for (String s : columnNames) {
            outputStream.print(prepend);
            outputStream.print(escape(s));
            prepend = "\t";
        }
        outputStream.print(prepend);
        outputStream.print("Duration");
        if (c.moveToFirst()) {
            Date start = new Date();
            Date end = new Date();
            Calendar cal = Calendar.getInstance();
            DecimalFormat nf = new DecimalFormat("0.0");
//            Date d = new Date();
            SimpleDateFormat formatter = new SimpleDateFormat("dd.MM.yyyy HH:mm");
            do {
                outputStream.println();
                prepend = "";
                for (int i=0; i<c.getColumnCount(); i++) {
                    outputStream.print(prepend);
                    String outValue;
                    if (columnNames[i].equals("start")) {
                        start.setTime(c.getLong(i));
                        outValue = formatter.format(start);                        
                    } else if (columnNames[i].equals("end")) {
                        if (c.isNull(i)) {
                            outValue = "";
                        } else {
                            end.setTime(c.getLong(i));
                            outValue = formatter.format(end);                        
                        }
                    } else {
                        outValue = escape(c.getString(i));
                    }
                    outputStream.print(outValue);
                    prepend = "\t";
                }
                cal.setTimeInMillis(end.getTime() - start.getTime());
                int h = cal.get(Calendar.HOUR) -1;
		int m = cal.get(Calendar.MINUTE);
		outputStream.print(prepend);
                outputStream.print(nf.format((float)h+((float)m/60f)));
                
            } while (c.moveToNext());
        }
        outputStream.println();
    }
}
