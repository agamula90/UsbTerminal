package com.ismet.usbterminal;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

import com.ismet.usbterminalnew.R;
import com.ismet.usbterminal.utils.RootDirectoryHandleUtils;

import java.io.File;

import fr.xgouchet.androidlib.ui.activity.AbstractBrowsingActivity;

import static fr.xgouchet.androidlib.ui.Toaster.showToast;

public class TedSaveAsActivity extends AbstractBrowsingActivity implements OnClickListener {

	/**
	 * the edit text input
	 */
	protected EditText mFileName;

	/** */
	protected Drawable mWriteable;

	/** */
	protected Drawable mLocked;

	/**
	 * @see android.app.Activity#onCreate(Bundle)
	 */
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Setup content view
		setContentView(R.layout.layout_save_as);

		// buttons
		findViewById(R.id.buttonCancel).setOnClickListener(this);
		findViewById(R.id.buttonOk).setOnClickListener(this);
		((Button) findViewById(R.id.buttonOk)).setText(R.string.ui_save);

		// widgets
		mFileName = (EditText) findViewById(R.id.editFileName);

		// drawables
		mWriteable = getResources().getDrawable(R.drawable.folder_rw);
		mLocked = getResources().getDrawable(R.drawable.folder_r);
	}

	/**
	 * @see OnClickListener#onClick(View)
	 */
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.buttonCancel:
				setResult(RESULT_CANCELED);
				finish();
				break;
			case R.id.buttonOk:
				if (setSaveResult())
					finish();
		}
	}

	/**
	 * @see AbstractBrowsingActivity#onFileClick(File)
	 */
	protected void onFileClick(File file) {
		if (file.canWrite())
			mFileName.setText(file.getName());
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
	 * Sets the result data when the user presses save
	 *
	 * @return if the result is OK (if not, it means the user must change its
	 * selection / input)
	 */
	protected boolean setSaveResult() {
		Intent result;
		String fileName;

		if ((mCurrentFolder == null) || (!mCurrentFolder.exists())) {
			showToast(this, R.string.toast_folder_doesnt_exist, true);
			return false;
		}

		if (!mCurrentFolder.canWrite()) {
			showToast(this, R.string.toast_folder_cant_write, true);
			return false;
		}

		fileName = mFileName.getText().toString();
		if (fileName.length() == 0) {
			showToast(this, R.string.toast_filename_empty, true);
			return false;
		}

		result = new Intent();
		result.putExtra("path", mCurrentFolder.getAbsolutePath() + File.separator + fileName);

		setResult(RESULT_OK, result);
		return true;
	}

	@Override
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
}
