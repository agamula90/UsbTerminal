package com.ismet.usbterminal;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;

import com.ismet.usbterminal.R;
import com.ismet.usbterminal.utils.RootDirectoryHandleUtils;

import java.io.File;

import de.neofonie.mobile.app.android.widget.crouton.Crouton;
import de.neofonie.mobile.app.android.widget.crouton.Style;
import fr.xgouchet.androidlib.ui.activity.AbstractBrowsingActivity;

import static fr.xgouchet.texteditor.common.Constants.EXTRA_REQUEST_CODE;
import static fr.xgouchet.texteditor.common.Constants.REQUEST_HOME_PAGE;
import static fr.xgouchet.texteditor.common.Constants.REQUEST_OPEN;

public class TedOpenActivity extends AbstractBrowsingActivity implements OnClickListener {

	/**
	 * @see Activity#onCreate(Bundle)
	 */
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		int request;
		Bundle extras;

		// Setup content view
		setContentView(R.layout.layout_open);
		mExtWhiteList.add("txt");
		mExtWhiteList.add("csv");

		// buttons
		findViewById(R.id.buttonCancel).setOnClickListener(this);

		// set default result
		setResult(RESULT_CANCELED, null);

		// show the title as toast
		extras = getIntent().getExtras();
		if (extras != null)
			request = extras.getInt(EXTRA_REQUEST_CODE);
		else
			request = -1;

		switch (request) {
			case REQUEST_OPEN:
				Crouton.showText(this, R.string.toast_open_select, Style.INFO);
				break;
			case REQUEST_HOME_PAGE:
				Crouton.showText(this, R.string.toast_home_page_select, Style.INFO);
				break;
		}

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
	 * @see AbstractBrowsingActivity#onFileClick(File)
	 */
	protected void onFileClick(File file) {
		if (setOpenResult(file))
			finish();
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
		result.putExtra("path", file.getAbsolutePath());

		setResult(RESULT_OK, result);
		return true;
	}
}
