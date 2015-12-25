package com.lamerman;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.proggroup.areasquarecalculator.R;

/**
 * Activity para escolha de arquivos/diretorios.
 *
 * @author android
 */
public class FileDialog extends ListActivity {

    /**
     * Chave de um item da lista de paths.
     */
    private static final String ITEM_KEY = "key";

    /**
     * Imagem de um item da lista de paths (diretorio ou arquivo).
     */
    private static final String ITEM_IMAGE = "image";

    /**
     * Diretorio raiz.
     */
    private static final String ROOT = "/";

    public static final String ROOT_PATH = "ROOT_PATH";

    /**
     * Parametro de entrada da Activity: path inicial. Padrao: ROOT.
     */
    public static final String START_PATH = "START_PATH";

    /**
     * Parametro de entrada da Activity: filtro de formatos de arquivos. Padrao:
     * null.
     */
    public static final String FORMAT_FILTER = "FORMAT_FILTER";

    /**
     * Parametro de saida da Activity: path escolhido. Padrao: null.
     */
    public static final String RESULT_PATH = "RESULT_PATH";

    public static final String MES_SELECTION_NAMES = "MES_SELECTION_NAMES";

    /**
     * Parametro de entrada da Activity: tipo de selecao: pode criar novos paths
     * ou nao. Padrao: nao permite.
     *
     * @see {@link SelectionMode}
     */
    public static final String SELECTION_MODE = "SELECTION_MODE";

    /**
     * Parametro de entrada da Activity: se e permitido escolher diretorios.
     * Padrao: falso.
     */
    public static final String CAN_SELECT_DIR = "CAN_SELECT_DIR";

    private List<String> path = null;
    private TextView myPath;
    private EditText mFileName;
    private ArrayList<HashMap<String, Object>> mList;
    private String mMesSelectionFiles[];

    private Button selectButton;

    private LinearLayout layoutSelect;
    private LinearLayout layoutCreate;
    private InputMethodManager inputManager;
    private String parentPath;
    private String currentPath = ROOT;

    private int selectionMode = SelectionMode.MODE_CREATE;

    private String[] formatFilter = null;

    private boolean canSelectDir = false;

    private File selectedFile;
    private HashMap<String, Integer> lastPositions = new HashMap<String, Integer>();

    private String rootPath;

    private Intent mInputIntent;

    /**
     * Called when the activity is first created. Configura todos os parametros
     * de entrada e das VIEWS..
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mInputIntent = getIntent();

        setResult(RESULT_CANCELED, mInputIntent);

        setContentView(R.layout.file_dialog_main);
        myPath = (TextView) findViewById(R.id.path);
        mFileName = (EditText) findViewById(R.id.fdEditTextFile);

        inputManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

        selectButton = (Button) findViewById(R.id.fdButtonSelect);
        selectButton.setEnabled(false);
        selectButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (selectedFile != null) {
                    mInputIntent.putExtra(RESULT_PATH, selectedFile.getPath());
                    setResult(RESULT_OK, mInputIntent);
                    finish();
                }
            }
        });

        final Button newButton = (Button) findViewById(R.id.fdButtonNew);
        newButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                setCreateVisible(v);

                mFileName.setText("");
                mFileName.requestFocus();
            }
        });

        selectionMode = mInputIntent.getIntExtra(SELECTION_MODE, SelectionMode.MODE_CREATE);

        formatFilter = mInputIntent.getStringArrayExtra(FORMAT_FILTER);

        canSelectDir = mInputIntent.getBooleanExtra(CAN_SELECT_DIR, false);

        rootPath = mInputIntent.getStringExtra(ROOT_PATH);

        if (rootPath == null) {
            rootPath = ROOT;
        }

        if (selectionMode == SelectionMode.MODE_OPEN) {
            newButton.setEnabled(false);
        }

        layoutSelect = (LinearLayout) findViewById(R.id.fdLinearLayoutSelect);
        layoutCreate = (LinearLayout) findViewById(R.id.fdLinearLayoutCreate);
        layoutCreate.setVisibility(View.GONE);

        final Button cancelButton = (Button) findViewById(R.id.fdButtonCancel);
        cancelButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                setSelectVisible(v);
            }

        });
        final Button createButton = (Button) findViewById(R.id.fdButtonCreate);
        createButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (mFileName.getText().length() > 0) {
                    mInputIntent.putExtra(RESULT_PATH, currentPath + "/" + mFileName.getText());
                    setResult(RESULT_OK, mInputIntent);
                    finish();
                }
            }
        });

        mMesSelectionFiles = mInputIntent.getStringArrayExtra(MES_SELECTION_NAMES);

        String startPath = mInputIntent.getStringExtra(START_PATH);
        startPath = startPath != null ? startPath : ROOT;

        if (canSelectDir) {
            File file = new File(startPath);
            selectedFile = file;
            selectButton.setEnabled(true);
        }
        getDir(startPath);
    }

    private void getDir(String dirPath) {

        boolean useAutoSelection = dirPath.length() < currentPath.length();

        Integer position = lastPositions.get(parentPath);

        getDirImpl(dirPath);

        if (position != null && useAutoSelection) {
            getListView().setSelection(position);
        }

    }

    /**
     * Monta a estrutura de arquivos e diretorios filhos do diretorio fornecido.
     *
     * @param dirPath Diretorio pai.
     */
    private void getDirImpl(final String dirPath) {

        currentPath = dirPath;

        final List<String> item = new ArrayList<>();
        path = new ArrayList<>();
        mList = new ArrayList<>();

        File f = new File(currentPath);
        File[] files = f.listFiles();
        if (files == null) {
            currentPath = ROOT;
            f = new File(currentPath);
            files = f.listFiles();
        }
        myPath.setText(getString(R.string.location) + ": " + currentPath);

        if (!currentPath.equals(ROOT)) {

            item.add(ROOT);
            addItem(ROOT, R.drawable.folder);
            path.add(ROOT);

            item.add("../");
            addItem("../", R.drawable.folder);
            path.add(f.getParent());
            parentPath = f.getParent();

        }

        TreeMap<String, String> dirsMap = new TreeMap<String, String>();
        TreeMap<String, String> dirsPathMap = new TreeMap<String, String>();
        TreeMap<String, String> filesMap = new TreeMap<String, String>();
        TreeMap<String, String> filesPathMap = new TreeMap<String, String>();
        for (File file : files) {
            if (file.isDirectory()) {
                String dirName = file.getName();
                dirsMap.put(dirName, dirName);
                dirsPathMap.put(dirName, file.getPath());
            } else {
                final String fileName = file.getName();
                final String fileNameLwr = fileName.toLowerCase();
                // se ha um filtro de formatos, utiliza-o
                if (formatFilter != null) {
                    boolean contains = false;
                    for (int i = 0; i < formatFilter.length; i++) {
                        final String formatLwr = formatFilter[i].toLowerCase();
                        if (fileNameLwr.endsWith(formatLwr)) {
                            contains = true;
                            break;
                        }
                    }
                    if (contains) {
                        filesMap.put(fileName, fileName);
                        filesPathMap.put(fileName, file.getPath());
                    }
                    // senao, adiciona todos os arquivos
                } else {
                    filesMap.put(fileName, fileName);
                    filesPathMap.put(fileName, file.getPath());
                }
            }
        }
        item.addAll(dirsMap.tailMap("").values());
        item.addAll(filesMap.tailMap("").values());
        path.addAll(dirsPathMap.tailMap("").values());
        path.addAll(filesPathMap.tailMap("").values());

        SimpleAdapter fileList = new SimpleAdapter(this, mList, R.layout.file_dialog_row, new String[]{
                ITEM_KEY, ITEM_IMAGE}, new int[]{R.id.fdrowtext, R.id.fdrowimage});

        for (String dir : dirsMap.tailMap("").values()) {
            addItem(dir, R.drawable.folder);
        }

        for (String file : filesMap.tailMap("").values()) {
            addItem(file, R.drawable.file);
        }

        fileList.notifyDataSetChanged();

        setListAdapter(fileList);
    }

    private void addItem(String fileName, int imageId) {
        HashMap<String, Object> item = new HashMap<>();
        item.put(ITEM_KEY, fileName);
        item.put(ITEM_IMAGE, imageId);
        mList.add(item);
    }

    private boolean mIsInsideSearchFolder;

    /**
     * Quando clica no item da lista, deve-se: 1) Se for diretorio, abre seus
     * arquivos filhos; 2) Se puder escolher diretorio, define-o como sendo o
     * path escolhido. 3) Se for arquivo, define-o como path escolhido. 4) Ativa
     * botao de selecao.
     */
    @Override
    protected void onListItemClick(ListView l, final View v, final int position, long id) {

        boolean mIsBackFolderClicked = position <= 1;
        if(mIsBackFolderClicked) {
            mIsInsideSearchFolder = false;
        }

        boolean mWasInsideSearchFolder = mIsInsideSearchFolder;

        final File file = new File(path.get(position));

        boolean mNewInsideSearchFolder = mWasInsideSearchFolder;

        if(mMesSelectionFiles != null) {
            for (String mMesSelectionFile : mMesSelectionFiles) {
                if (file.getAbsolutePath().endsWith(mMesSelectionFile)) {
                    mNewInsideSearchFolder = true;
                }
            }
        }

        if(mIsInsideSearchFolder) {
            android.support.v7.app.AlertDialog dialog = new android.support.v7.app
                    .AlertDialog.Builder(this).setNeutralButton(getResources().getString
                    (R.string.select_folder_for_auto), new DialogInterface
                    .OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mInputIntent.putExtra(RESULT_PATH, file.getAbsolutePath());
                    setResult(RESULT_OK, mInputIntent);
                    dialog.dismiss();
                    finish();
                }
            }).setPositiveButton(getResources().getString(R.string
                    .select_file_for_calculations), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    canSelectDir = false;
                    mIsInsideSearchFolder = false;
                    handleItemClick(file, v, position);
                    dialog.dismiss();
                }
            }).setMessage(getResources().getString(R.string.select_option_to_continue))
                    .create();
            dialog.show();
            View decorView = dialog.getWindow().getDecorView();
            ((TextView)decorView.findViewById(android.R.id.message)).setGravity(Gravity
                    .CENTER);

            Button button3 = ((Button) decorView.findViewById(android.R.id.button3));
            button3.setTextColor(Color.BLACK);
            if(Build.VERSION.SDK_INT >= 16) {
                button3.setBackground(getResources().getDrawable(R.drawable
                        .button_drawable));
            } else {
                button3.setBackgroundDrawable(getResources().getDrawable(R.drawable
                        .button_drawable));
            }
            Button button1 = ((Button) decorView.findViewById(android.R.id.button1));
            button1.setTextColor(Color.BLACK);
            if(Build.VERSION.SDK_INT >= 16) {
                button1.setBackground(getResources().getDrawable(R.drawable
                        .button_drawable));
            } else {
                button1.setBackgroundDrawable(getResources().getDrawable(R.drawable
                        .button_drawable));
            }
            return;
        }

        handleItemClick(file, v, position);

        mIsInsideSearchFolder = mNewInsideSearchFolder;
    }

    private void handleItemClick(File file, View v, int position) {
        if (file.isDirectory()) {
            if (!rootPath.equals(ROOT)) {
                if (file.getAbsolutePath().equals(ROOT) || file.getAbsolutePath().equals(new File
                        (rootPath).getParentFile().getAbsolutePath())) {
                    finish();
                    return;
                }
            } else if (file.getAbsolutePath().equals(ROOT)) {
                finish();
                return;
            }

            setSelectVisible(v);

            selectButton.setEnabled(false);
            if (file.canRead()) {
                lastPositions.put(currentPath, position);
                getDir(path.get(position));
                if (canSelectDir) {
                    selectedFile = file;
                    v.setSelected(true);
                    selectButton.setEnabled(true);
                }
            } else {
                new AlertDialog.Builder(this).setIcon(R.drawable.icon)
                        .setTitle("[" + file.getName() + "] " + getString(R.string.cant_read_folder))
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        }).show();
            }
        } else {
            setSelectVisible(v);
            selectedFile = file;
            v.setSelected(true);
            selectButton.setEnabled(true);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {
            selectButton.setEnabled(false);

            if (layoutCreate.getVisibility() == View.VISIBLE) {
                layoutCreate.setVisibility(View.GONE);
                layoutSelect.setVisibility(View.VISIBLE);
            } else {
                if (!currentPath.equals(rootPath)) {
                    getDir(parentPath);
                } else {
                    return super.onKeyDown(keyCode, event);
                }
            }

            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    /**
     * Define se o botao de CREATE e visivel.
     *
     * @param v
     */

    private void setCreateVisible(View v) {
        layoutCreate.setVisibility(View.VISIBLE);
        layoutSelect.setVisibility(View.GONE);

        inputManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
        selectButton.setEnabled(false);
    }

    /**
     * Define se o botao de SELECT e visivel.
     *
     * @param v
     */
    private void setSelectVisible(View v) {
        layoutCreate.setVisibility(View.GONE);
        layoutSelect.setVisibility(View.VISIBLE);

        inputManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
        selectButton.setEnabled(false);
    }
}
