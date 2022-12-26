package fr.xgouchet;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;

import fr.xgouchet.R;

import java.io.File;
import java.util.LinkedList;

import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;
import fr.xgouchet.androidlib.ui.activity.AbstractBrowsingActivity;
import fr.xgouchet.texteditor.ui.adapter.FontListAdapter;
import fr.xgouchet.utils.RootDirectoryHandleUtils;

public class TedFontActivity extends AbstractBrowsingActivity implements OnClickListener {

	/**
	 * @see Activity#onCreate(Bundle)
	 */
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.layout_open);
		mExtWhiteList.add("ttf");

		// set default result
		setResult(RESULT_CANCELED, null);

		// buttons
		findViewById(R.id.buttonCancel).setOnClickListener(this);

		mListAdapter = new FontListAdapter(this, new LinkedList<File>());
	}

	/**
	 * @see AbstractBrowsingActivity#onFileClick(File)
	 */
	protected void onFileClick(File file) {
		if (setOpenResult(file))
			finish();
	}

	/**
	 * @see AbstractBrowsingActivity#onFolderClick(File)
	 */
	protected boolean onFolderClick(File folder) {
		RootDirectoryHandleUtils.handleEnvironmentStorageDirectory(this, folder, false);
		return true;
	}

	/**
	 * @see AbstractBrowsingActivity#onFolderViewFilled()
	 */
	protected void onFolderViewFilled() {

	}

	/**
	 * @see Activity#onKeyUp(int, KeyEvent)
	 */
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
			// navigate to parent folder
			File parent = mCurrentFolder.getParentFile();

			if (RootDirectoryHandleUtils.handleEnvironmentStorageDirectory(this, mCurrentFolder,
					true)) {
				return true;
			} else if ((parent != null) && (parent.exists())) {
				fillFolderView(parent);
				return true;
			}
		}
		return super.onKeyUp(keyCode, event);
	}

	/**
	 * @see OnClickListener#onClick(View)
	 */
	public void onClick(View v) {
		if (v.getId() == R.id.buttonCancel) {
			setResult(RESULT_CANCELED);
			finish();
		}
	}

	/**
	 * Set the result of this activity to open a file
	 *
	 * @param file the file to return
	 * @return if the result was set correctly
	 */
	protected boolean setOpenResult(File file) {
		Intent result;

		if (!file.canRead()) {
			Crouton.showText(this, R.string.toast_file_cant_read, Style.ALERT);
			return false;
		}

		result = new Intent();
		result.setData(Uri.fromFile(file));

		setResult(RESULT_OK, result);
		return true;
	}
}
