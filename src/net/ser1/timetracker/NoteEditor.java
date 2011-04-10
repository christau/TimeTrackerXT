package net.ser1.timetracker;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

public class NoteEditor extends Activity
{
	public static final String NOTE = "note";

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		getIntent().getExtras().getLong(DBHelper.START);
		getIntent().getExtras().getLong(DBHelper.TASK_ID);
		setContentView(R.layout.note_editor);
		((Button)findViewById(R.id.btnCancel)).setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				setResult(Activity.RESULT_CANCELED, getIntent());
				finish();
			}
		});
		((Button)findViewById(R.id.btnOk)).setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				String note = ((EditText)findViewById(R.id.txtNote)).getText().toString();
//				getIntent().getExtras().putString(NOTE, note);
				Intent intent = new Intent(NoteEditor.this, NoteEditor.class);
			        intent.putExtra(NOTE, note);
			        intent.putExtra(DBHelper.TASK_ID, getIntent().getIntExtra(DBHelper.TASK_ID, -1));
			        intent.putExtra(DBHelper.START, getIntent().getLongExtra(DBHelper.START, -1));
				setResult(Activity.RESULT_OK, intent);
				finish();
			}
		});
	}

	@Override
	public void finish()
	{
		super.finish();
	}
}
