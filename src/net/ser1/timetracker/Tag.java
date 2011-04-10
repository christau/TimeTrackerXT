/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.ser1.timetracker;

import java.io.Serializable;

/**
 *
 * @author ser
 */
public class Tag implements Serializable {
    private static final long serialVersionUID = 5557740387803468041L;
    private int[] tasks = {};
    private String name;

    public Tag( String name ) {
        this.name = name.toLowerCase();
    }

    public String getName() {
        return name;
    }

    public int[] getTasks() {
        return tasks;
    }

    public void addTask( Task t ) {
        addTask(t.getId());
    }

    /**
     * Adds a task to this tag IFF the task is not already associated with this 
     * tag.  NOOP if the task is already assocatiated.
     * @param id the Task.id of the task
     */
    public void addTask( int id ) {
        for (int searchItem : tasks) {
            if (searchItem == id) return;
        }
        int[] oldTasks = tasks;
        tasks = new int[oldTasks.length + 1];
        System.arraycopy(oldTasks, 0, tasks, 0, oldTasks.length);
        tasks[oldTasks.length] = id;
    }

    protected void setTasks( int[] tasks ) {
        this.tasks = tasks;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
    @Override
    public boolean equals( Object other ) {
        if (!(other instanceof Tag)) return false;
        Tag tag = (Tag)other;
        return name.equals(tag.getName());
    }
}
