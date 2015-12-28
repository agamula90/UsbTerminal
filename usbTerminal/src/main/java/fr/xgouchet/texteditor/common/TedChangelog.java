package fr.xgouchet.texteditor.common;

import com.ismet.usbterminal.R;

import fr.xgouchet.androidlib.common.AbstractChangeLog;

public class TedChangelog extends AbstractChangeLog {

    /**
     * @see fr.xgouchet.androidlib.common.ChangeLog#getTitleResourceForVersion(int)
     */
    public int getTitleResourceForVersion(int version) {
        int res = 0;
        switch (version) {
            case 18:
            default:
                res = R.string.release18;
        }
        return res;
    }

    /**
     * @see fr.xgouchet.androidlib.common.ChangeLog#getChangeLogResourceForVersion(int)
     */
    public int getChangeLogResourceForVersion(int version) {
        int res = 0;
        switch (version) {
            case 18:
            default:
                res = R.string.release18_log;
        }
        return res;
    }

}
