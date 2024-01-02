package fr.xgouchet.androidlib.ui.activity;

import android.annotation.TargetApi;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.view.Menu;
import android.view.MenuItem;

public final class ActivityDecorator {

	private ActivityDecorator() {
	}

	/**
	 * Adds a Menu item either in the Option Menu (pre Honeycomb) or in the
	 * action bar
	 *
	 * @param menu   the menu to fill
	 * @param itemId the id of this item
	 * @param title  the title resource
	 * @param icon   the drawable (for pre honeycomb option menu)
	 * @return the menu item
	 */
	public static MenuItem addMenuItem(final Menu menu, final int itemId, final int title, final
	int icon) {
		MenuItem item;

		item = menu.add(0, itemId, Menu.NONE, title);
		return item;
	}

	/**
	 * Show the menu item as an action in the action bar
	 *
	 * @param item the item to show
	 * @param icon the item icon resource
	 */
	public static void showMenuItemAsAction(final MenuItem item, final int icon, int action) {
		if (icon != -1) {
			item.setIcon(icon);
		}
		item.setShowAsAction(action);
	}
}
