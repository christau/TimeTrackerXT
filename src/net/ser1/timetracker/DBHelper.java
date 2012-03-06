/**
 * TimeTracker 
 * ©2008, 2009 Sean Russell
 * @author Sean Russell <ser@germane-software.com>
 */
package net.ser1.timetracker;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DBHelper extends SQLiteOpenHelper
{
	// Table names
	public static final String RANGES_TABLE = "ranges";
	public static final String TASK_TABLE = "tasks";
	public static final String TAGS_TABLE = "tags";

	public static final String NOTE = "note";
	public static final String END = "end";
	public static final String START = "start";
	public static final String TASK_ID = "task_id";
	public static final String[] RANGE_COLUMNS = { START, END, NOTE };
	public static final String NAME = "name";
	public static final String[] TASK_COLUMNS = new String[] { "ROWID", NAME };
	public static final String TIMETRACKER_DB_NAME = "timetracker.db";
	public static final int DBVERSION = 8;
	public static final String TASK_NAME = "name";
	public static final String ID_NAME = "_id";
	public static final String TAG = "tagname";
	private static final String CREATE_TAGS_TABLE = "CREATE TABLE " + TAGS_TABLE + "(" + TASK_ID + " INTEGER NOT NULL," + TAG + " TEXT NOT NULL );";

	public DBHelper(Context context)
	{
		super(context, TIMETRACKER_DB_NAME, null, DBVERSION);
		instance = this;
	}

	/**
	 * Despite the name, this is not a singleton constructor
	 */
	private static DBHelper instance;

	public static DBHelper getInstance()
	{
		return instance;
	}

	private static final String CREATE_TASK_TABLE = "CREATE TABLE %s (" + ID_NAME + " INTEGER PRIMARY KEY AUTOINCREMENT," + TASK_NAME + " TEXT COLLATE LOCALIZED NOT NULL" + ");";

	@Override
	public void onCreate(SQLiteDatabase db)
	{
		db.execSQL(String.format(CREATE_TASK_TABLE, TASK_TABLE));
		db.execSQL("CREATE TABLE " + RANGES_TABLE + "(" + TASK_ID + " INTEGER NOT NULL," + START + " INTEGER NOT NULL," + END + " INTEGER," + NOTE + " TEXT" + ");");
		db.execSQL(CREATE_TAGS_TABLE);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
	{
		if (oldVersion < 4)
		{
			db.execSQL(String.format(CREATE_TASK_TABLE, "temp"));
			db.execSQL("insert into temp(rowid," + TASK_NAME + ") select rowid," + TASK_NAME + " from " + TASK_TABLE + ";");
			db.execSQL("drop table " + TASK_TABLE + ";");
			db.execSQL(String.format(CREATE_TASK_TABLE, TASK_TABLE));
			db.execSQL("insert into " + TASK_TABLE + "(" + ID_NAME + "," + TASK_NAME + ") select rowid," + TASK_NAME + " from temp;");
			db.execSQL("drop table temp;");
		} else if (oldVersion < 5)
		{
			db.execSQL(String.format(CREATE_TASK_TABLE, "temp"));
			db.execSQL("insert into temp(" + ID_NAME + "," + TASK_NAME + ") select rowid," + TASK_NAME + " from " + TASK_TABLE + ";");
			db.execSQL("drop table " + TASK_TABLE + ";");
			db.execSQL(String.format(CREATE_TASK_TABLE, TASK_TABLE));
			db.execSQL("insert into " + TASK_TABLE + "(" + ID_NAME + "," + TASK_NAME + ") select " + ID_NAME + "," + TASK_NAME + " from temp;");
			db.execSQL("drop table temp;");
		} else if (oldVersion < 7)
		{
			db.execSQL("CREATE TABLE temp (" + TASK_ID + " INTEGER NOT NULL," + START + " INTEGER NOT NULL," + END + " INTEGER," + NOTE + " TEXT" + ");");

			db.execSQL("insert into temp(" + TASK_ID + "," + START + "," + END + ") select " + TASK_ID + "," + START + "," + END + " from " + RANGES_TABLE + ";");

			db.execSQL("drop table " + RANGES_TABLE + ";");

			db.execSQL("CREATE TABLE " + RANGES_TABLE + "(" + TASK_ID + " INTEGER NOT NULL," + START + " INTEGER NOT NULL," + END + " INTEGER," + NOTE + " TEXT" + ");");

			db.execSQL("insert into " + RANGES_TABLE + "(" + TASK_ID + "," + START + "," + END + ") select " + TASK_ID + "," + START + "," + END + " from temp;");
			db.execSQL("drop table temp;");

		} else if (oldVersion < 8)
		{
			db.execSQL("CREATE TABLE temp (" + ID_NAME + " INTEGER PRIMARY KEY AUTOINCREMENT," + TASK_ID + " INTEGER NOT NULL," + START + " INTEGER NOT NULL," + END + " INTEGER" + ");");

			db.execSQL("insert into temp(" + TASK_ID + "," + START + "," + END + ") select " + TASK_ID + "," + START + "," + END + " from " + RANGES_TABLE + ";");

			db.execSQL("drop table " + RANGES_TABLE + ";");

			db.execSQL("CREATE TABLE " + RANGES_TABLE + "(" + TASK_ID + " INTEGER NOT NULL," + START + " INTEGER NOT NULL," + END + " INTEGER," + NOTE + " TEXT" + ");");

			db.execSQL("insert into " + RANGES_TABLE + "(" + TASK_ID + "," + START + "," + END + ") select " + TASK_ID + "," + START + "," + END + " from temp;");
			db.execSQL("drop table temp;");

		} else if (oldVersion < 16)
		{
			// db.execSQL(CREATE_TAGS_TABLE);
		}
	}
}