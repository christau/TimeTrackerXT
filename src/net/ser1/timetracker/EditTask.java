package net.ser1.timetracker;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

public class EditTask extends Activity implements OnClickListener {

    private static final String LAST_TAG = "last_tag";
    public static final String TASK_ID = "TaskID";
    public static final String TASK_NAME = "TaskName";
    public static final String TAGS = "Tags";
    private int taskId;
    private ArrayAdapter<Tag> tagsModel;
    private TagHandler tagHandler = null;
    private TagsAdapter selectedTags;

    @SuppressWarnings("unchecked")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        tagHandler = new TagHandler(this);
        
        Bundle extras = getIntent().getExtras();
        taskId = extras.getInt(TASK_ID);
        Set<Tag> tags = (Set<Tag>) extras.getSerializable(TAGS);
        if (tags == null) {
            setResult(Activity.RESULT_CANCELED, getIntent());
            finish();
        }
        
        LayoutInflater factory = LayoutInflater.from(this);
        setContentView(factory.inflate(R.layout.edit_task, null));
        tagsModel = new ArrayAdapter<Tag>(this,
                android.R.layout.simple_spinner_item);
        tagsModel.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        for (Tag t : tags) {
            tagsModel.add(t);
        }
        
        EditText text = (EditText)findViewById(R.id.task_edit_name_edit);
        String taskName = extras.getString(TASK_NAME);
        if (savedInstanceState != null) {
            String backupName = savedInstanceState.getString(TASK_NAME); 
            if (backupName != null && !backupName.equals("")) {
                taskName = savedInstanceState.getString(TASK_NAME);
            }
            if (savedInstanceState.getString(LAST_TAG) != null) {
                EditText tagText = (EditText)findViewById(R.id.new_tag_edit);
                tagText.setText(savedInstanceState.getString(LAST_TAG));
            }
        }
        text.setText(taskName);
        ListView l = (ListView)findViewById(R.id.tags);
        selectedTags = new TagsAdapter(tagHandler.getTags(taskId));
        l.setAdapter(selectedTags);
        Spinner tagView = (Spinner) findViewById(R.id.tag_chooser);
        tagView.setAdapter(tagsModel);
        ((ImageButton) findViewById(R.id.add_tag_spinner)).setOnClickListener(this);
        ((ImageButton) findViewById(R.id.add_tag)).setOnClickListener(this);
        if (taskId == -1) {
            ((Button)findViewById(R.id.new_task_cancel)).setVisibility(View.VISIBLE);
        } else {
            ((Button)findViewById(R.id.new_task_cancel)).setVisibility(View.GONE);
        }

        super.onCreate(savedInstanceState);
    }

    @Override
    public void finish() {
        EditText text = (EditText) findViewById(R.id.task_edit_name_edit);
        Intent intent = new Intent(this, EditTask.class);
        intent.putExtra(TASK_NAME, text.getText().toString());
        intent.putExtra(TASK_ID, taskId);
        ListView l = (ListView)findViewById(R.id.tags);
        TagsAdapter tagsAdapter = (TagsAdapter)l.getAdapter();
        intent.putExtra(TAGS, (Serializable)tagsAdapter.getTags());
        setResult(Activity.RESULT_OK, intent);
        super.finish();
    }
    
    public void onClick(View v) {
        String name = null;
        Tag newTag = null;
        Spinner tagChooser = (Spinner)findViewById(R.id.tag_chooser);
        switch (v.getId()) {
        case R.id.add_tag:
            name = ((EditText)findViewById(R.id.new_tag_edit)).getText().toString();
            if (name != null) {
                newTag = new Tag(name);
                if (tagsModel.getPosition(newTag) == -1) {
                    tagsModel.add(newTag);
                    tagsModel.notifyDataSetChanged();
                    tagChooser.invalidate();
                }
            }
            break;
        case R.id.add_tag_spinner:
            name = tagsModel.getItem(tagChooser.getSelectedItemPosition()).getName();
            newTag = new Tag(name);
            break;
        default: // it is a tag delete call
            Tag tag = (Tag)v.getTag();
            selectedTags.remove(tag);
            selectedTags.notifyDataSetChanged();
            break;
        }
        if (newTag != null && selectedTags.getPosition(newTag) == -1) {
            selectedTags.add(newTag);
            selectedTags.notifyDataSetChanged();
        }
        findViewById(R.id.tags).invalidate();
        findViewById(R.id.edit_task_root).invalidate();
    }
    
    @Override
    protected void onSaveInstanceState( Bundle bundle ) {
        EditText text = (EditText) findViewById(R.id.task_edit_name_edit);
        bundle.putString(TASK_NAME, text.getText().toString());
        EditText tagname = (EditText) findViewById(R.id.new_tag_edit);
        bundle.putString(LAST_TAG, tagname.getText().toString());
    }

    
    
    private class TagsAdapter extends ArrayAdapter<Tag> {
        protected TagsAdapter( List<Tag> taskTags ) {
            super(getBaseContext(), R.id.tags, taskTags);
        }
        
        protected Set<Tag> getTags() {
            Set<Tag> rv = new HashSet<Tag>(getCount());
            for (int i=0; 
                 i<getCount(); 
                 rv.add(getItem(i++)));
            return rv;
        }

        public View getView(int position, View oldView, ViewGroup arg2) {
            TagView view = null;
            if (oldView == null) {
                Object item = getItem(position);
                if (item != null) {
                    view = new TagView(getBaseContext(), (Tag) item);
                }
            } else {
                view = (TagView) oldView;
                Object item = getItem(position);
                if (item != null) {
                    view.setTag((Tag) item);
                }
            }
            return view;
        }

        private class TagView extends LinearLayout {
            private TextView tagName;
            private ImageButton delete;

            public TagView(Context context, Tag t) {
                super(context);
                setOrientation(LinearLayout.HORIZONTAL);
                setPadding(5, 2, 5, 2);

                int fontSize = getSharedPreferences( Tasks.TIMETRACKERPREF,MODE_PRIVATE )
                    .getInt(Tasks.FONTSIZE, 16);
                
                tagName = new TextView(context);
                tagName.setTextSize(fontSize);
                tagName.setText(t.getName());
                addView(tagName, new LinearLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT, 
                        LayoutParams.FILL_PARENT, 1f ));

                delete = new ImageButton(context, null, android.R.style.Widget_Button_Small);
                delete.setImageResource(android.R.drawable.ic_delete);
                addView(delete, new LinearLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 0f
                        ));
                delete.setTag(t);
                delete.setOnClickListener(EditTask.this);
                
                setGravity(Gravity.TOP);
            }

            protected void setTag(Tag t) {
                tagName.setText(t.getName());
                delete.setTag(t);
            }
        }
    }
}