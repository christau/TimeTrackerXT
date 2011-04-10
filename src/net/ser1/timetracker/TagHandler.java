/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.ser1.timetracker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static net.ser1.timetracker.DBHelper.TAGS_TABLE;
import static net.ser1.timetracker.DBHelper.TAG;
import static net.ser1.timetracker.DBHelper.TASK_ID;

/**
 * This isn't thread-safe.
 * @author ser
 */
public class TagHandler {

    private static final String[] TAGS_COLUMNS = new String[] { TAG, TASK_ID };
    private static final String MATCH_TASK_TAG = TAG+" = ? AND "+TASK_ID+" = ?";
    public static final String TAG_EXISTS = "SELECT COUNT(*) FROM " + TAGS_TABLE + " WHERE " + TAG + " = ?";
    private final DBHelper dbHelper;
    private ContentValues values = new ContentValues();

    public TagHandler(Context context) {
        dbHelper = new DBHelper(context);
        dbHelper.getWritableDatabase();        
    }
    
    
    private static final String TAG_EQ = TAG + " = ?";

    /**
     * Removes a tag, and all task associations.  No-op if no such tag exists.
     * @param name the name of the tag to remove
     */
    protected void deleteTag(String name) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(TAGS_TABLE, TAG_EQ, new String[]{name.toLowerCase()});
        db.close();
    }


    /**
     * Alters a tag's name, leaving all associations. Fails with no effect if
     * the target tag name already exists.
     * @param oldName
     * @param newName
     */
    protected boolean modifyTag(String oldName, String newName) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        int count = db.rawQuery(TAG_EXISTS, new String[]{oldName.toLowerCase()}).getCount();
        db.close();
        if (count > 0) {
            return false;
        } else {
            mergeTags(oldName, newName);
            return true;
        }
    }


    /**
     * Merges all tasks associated with oldname into newname, and removes
     * oldname. If newTag does not exist, it is created.  This doesn't fail.
     * @param oldTag the name of the tag to move tasks from
     * @param newTag the name of the tag to move tasks to
     */
    protected void mergeTags(String oldTag, String newTag) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        values.clear();
        values.put(TAG, newTag.toLowerCase());
        db.update(TAGS_TABLE, values, TAG_EQ, new String[]{oldTag.toLowerCase()});
        db.close();
    }

    
    private final String[] COLUMNS = { TASK_ID };
    
    /**
     * Returns a list of all task IDs associated with a tag.
     * @param tagName
     * @return
     */
    public int[] getTasks(String tagName) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(TAGS_TABLE, COLUMNS, TAG_EQ,
                new String[]{tagName.toLowerCase()}, null, null, null );
        int[] rv = new int[cursor.getCount()];
        int idx = 0;
        if (cursor.moveToFirst()) {
            do {
                rv[idx++] = cursor.getInt(0);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return rv;
    }

    
    public String[] getTagNames() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(true,TAGS_TABLE,new String[]{TAG},
                null,null,null,null,null,null);
        String[] rv = new String[ cursor.getCount() ];
        int ctr = 0;
        if (cursor.moveToFirst()) {
            do {
                rv[ctr++] = cursor.getString(0);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return rv;
    }
    
    /**
     * @param task
     * @return
     */
    public List<Tag> getTags( Task task ) {
        return getTags( task.getId() );
    }

    public List<Tag> getTags( int taskId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(TAGS_TABLE, new String[] { TAG }, 
                TASK_ID+" = ?", new String[] { String.valueOf(taskId) }, 
                null, null, null);
        List<Tag> rv = new ArrayList<Tag>(c.getCount());
        if (c.moveToFirst()) {
            do {
                String tn = c.getString(0);
                rv.add(new Tag(tn));
            } while (c.moveToNext());
        }
        c.close();
        db.close();
        return rv;
    }

    public Set<Tag> getTags() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(TAGS_TABLE, TAGS_COLUMNS,
                null,null,null,null,TAG);
        
        // Optimization.  Throw task IDs into a buffer, and copy into the tag
        // when done.
        String currentTag = null;
        // The number of tasks in a tag will never be larger than the total number
        // of task/tag pairs, and that's not going to be very large -- so just
        // use the number of pairs for the buffer length
        int[] buffer = new int[c.getCount()];
        int bufferLength = 0;

        HashMap<String,Tag> rv = new HashMap<String,Tag>( c.getCount() );
        if (c.moveToFirst()) {
            do {
                String tn = c.getString(0);
                Tag t = rv.get(tn);
                if (t == null) {
                    t = new Tag(tn);
                    rv.put(tn, t);
                }
                if (!tn.equals(currentTag)) {
                    if (currentTag != null) {
                        setTagTasksFromBuffer(currentTag, buffer, bufferLength, rv);
                        // Reset the buffer
                        bufferLength = 0;
                    }
                    currentTag = tn;
                }
                buffer[bufferLength++] = c.getInt(1);
            } while (c.moveToNext());
            // The last task doesn't get updated in the loop
            setTagTasksFromBuffer(currentTag, buffer, bufferLength, rv);                
        }
        Set<Tag> retVal = new HashSet<Tag>(rv.size());
        retVal.addAll(rv.values());
        c.close();
        db.close();
        return retVal;
    }


    private void setTagTasksFromBuffer(String currentTag, int[] buffer,
            int bufferLength, HashMap<String, Tag> rv) {
        // Copy from the buffer into a task list for the old
        // tag, and then set the task list
        int[] taskIds = new int[bufferLength];
        System.arraycopy(buffer, 0, taskIds, 0, bufferLength);
        rv.get(currentTag).setTasks(taskIds);
    }

    protected void tag(Task task, Tag tag) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        int count = db.query(TAGS_TABLE, new String[]{TAG}, 
                MATCH_TASK_TAG,
                new String[]{tag.getName(),String.valueOf(task.getId())},
                null,null,null).getCount();
        db.close();
        if (count > 0) {
            return;
        } else {
            tag.addTask(task);

            values.clear();
            values.put(TASK_ID, task.getId());
            values.put(TAG, tag.getName());

            db = dbHelper.getWritableDatabase();
            db.insert(TAGS_TABLE, null, values);
            db.close();
        }
    }

    protected void untag(Task task, Tag tag) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String[] whereClause = new String[]{tag.getName(),String.valueOf(task.getId())};
        Cursor c = db.query(TAGS_TABLE, new String[]{TAG}, MATCH_TASK_TAG,
                whereClause,
                null,null,null);
        int count = c.getCount();
        c.close();
        db.close();
        if (count != 0) {
            db = dbHelper.getWritableDatabase();
            db.delete(TAGS_TABLE, MATCH_TASK_TAG, whereClause);
            db.close();
        }
    }
}