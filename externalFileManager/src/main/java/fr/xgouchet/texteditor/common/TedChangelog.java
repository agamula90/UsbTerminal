package fr.xgouchet.texteditor.common;

import fr.xgouchet.R;

import fr.xgouchet.androidlib.common.AbstractChangeLog;

public class TedChangelog extends AbstractChangeLog {

    /**
     * @see fr.xgouchet.androidlib.common.AbstractChangeLog#getTitleResourceForVersion(int)
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
     * @see fr.xgouchet.androidlib.common.AbstractChangeLog#getChangeLogResourceForVersion(int)
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
