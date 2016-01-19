package com.proggroup.areasquarecalculator.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintJob;
import android.print.PrintManager;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.view.GravityCompat;
import android.text.Html;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.lamerman.FileDialog;
import com.lamerman.SelectionMode;
import com.proggroup.areasquarecalculator.InterpolationCalculatorApp;
import com.proggroup.areasquarecalculator.R;
import com.proggroup.areasquarecalculator.api.LibraryContentAttachable;
import com.proggroup.areasquarecalculator.data.AvgPoint;
import com.proggroup.areasquarecalculator.data.Constants;
import com.proggroup.areasquarecalculator.data.ReportData;
import com.proggroup.areasquarecalculator.tasks.CreateCalibrationCurveForAutoTask;
import com.proggroup.areasquarecalculator.utils.FloatFormatter;
import com.proggroup.areasquarecalculator.utils.IntentFolderWrapUtils;
import com.proggroup.areasquarecalculator.utils.ReportCreator;
import com.proggroup.squarecalculations.CalculateUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BottomFragment extends Fragment {

    /**
     * Request code for start load ppm curve file dialog.
     */
    private static final int LOAD_PPM_AVG_VALUES_REQUEST_CODE = 103;

    private static final int MES_SELECT_FOLDER = 104;

    private static final String IS_SAVED = "is_saved";

    private static final String THIRD_TEXT_TAG = "third_texxt";

    private static final String FOURTH_TEXT_TAG = "fourth_text";

    private static final SimpleDateFormat FORMATTER = new SimpleDateFormat
            ("yyyyMMdd_HHmmss");

    private View loadPpmCurve;

    private View graph1;

    private View mesSelectFolder;

    private View calculatePpmLayoutLoaded;

    private EditText avgValueLoaded;

    private View calculatePpmSimpleLoaded, calculatePpmAuto;

    private TextView resultPpmLoaded;

    private View mReport;

    private View mClearRow2;

    private LinearLayout avgPointsLayout;

    private List<Float> ppmPoints;

    private List<Float> avgSquarePoints;

    private AvgPoint mAutoAvgPoint;

    private File mCurveFile;

    private File mAvgFiles[];

    private boolean mAutoSelected;

    private boolean mDoPostLoadingCalculations;

    private LoadGraphDataTask.OnGraphDataLoadedCallback onGraphDataLoadedCallback;

    private View.OnClickListener mRealCalculationsCalculateAutoListener;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setRetainInstance(true);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable
    Bundle savedInstanceState) {
        View contentView = inflater.inflate(R.layout.layout_bottom, container, false);

        avgPointsLayout = (LinearLayout) contentView.findViewById(R.id.avg_points);
        return contentView;
    }

    @Override
    public void onViewCreated(final View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        loadPpmCurve = view.findViewById(R.id.load_ppm_curve);
        graph1 = view.findViewById(R.id.graph);
        mesSelectFolder = view.findViewById(R.id.mes_select_folder);
        calculatePpmLayoutLoaded = view.findViewById(R.id.calculate_ppm_layout_loaded);
        avgValueLoaded = (EditText) view.findViewById(R.id.avg_value_loaded);
        calculatePpmSimpleLoaded = view.findViewById(R.id.calculate_ppm_loaded);
        calculatePpmAuto = view.findViewById(R.id.calculate_ppm_auto);
        resultPpmLoaded = (TextView) view.findViewById(R.id.result_ppm_loaded);
        mClearRow2 = view.findViewById(R.id.clear_row);

        mReport = view.findViewById(R.id.report);

        mReport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (resultPpmLoaded.getText().length() == 0) {
                    Toast.makeText(getActivity(), "Do auto calculations!", Toast.LENGTH_LONG)
                            .show();
                    return;
                }

                Activity activity = getActivity();
                LibraryContentAttachable libraryContentAttachable = activity instanceof
                        LibraryContentAttachable ? (LibraryContentAttachable) activity : null;

                if (libraryContentAttachable != null) {
                    LinearLayout viewGroup = libraryContentAttachable.graphContainer();
                    viewGroup.removeAllViews();

                    FrameLayout frameLayout = new FrameLayout(activity);
                    viewGroup.addView(frameLayout, new LinearLayout.LayoutParams(ViewGroup
                            .LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                    final WebView webView = new WebView(activity);
                    frameLayout.addView(webView, new LinearLayout.LayoutParams(ViewGroup
                            .LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                    libraryContentAttachable.onGraphAttached();

                    ReportData reportData = new ReportData();
                    reportData.setPpm(Float.parseFloat(resultPpmLoaded.getText().toString()));

                    File parentFile = mAvgFiles[0].getParentFile();

                    reportData.setMeasurementFolder(parentFile.getName());

                    List<String> measurementFiles = new ArrayList<>();

                    for (File avgFile : mAvgFiles) {
                        measurementFiles.add(avgFile.getName());
                    }

                    reportData.setMeasurementFiles(measurementFiles);

                    List<Float> measurementValues = mAutoAvgPoint.getValues();
                    reportData.setMeasurementAverages(measurementValues);

                    reportData.setCalibrationCurveFolder(mCurveFile.getName());

                    reportData.setPpmData(ppmPoints);
                    reportData.setAvgData(avgSquarePoints);
                    reportData.setCountMeasurements(mAvgFiles.length);

                    String htmlText = Html.toHtml(ReportCreator.createReport(ReportCreator
                            .defaultReport(reportData, libraryContentAttachable)));

                    int reportNumber = ReportCreator.countReports(libraryContentAttachable
                            .reportFolders());

                    final String fileName = ReportCreator.REPORT_START_NAME + FORMATTER.format
                            (libraryContentAttachable.currentDate()) + "_" + reportNumber;

                    libraryContentAttachable.writeReport(htmlText, fileName);

                    webView.loadDataWithBaseURL(null, htmlText, null, "UTF-8", null);

                    Button button = new Button(getActivity());
                    button.setTextAppearance(getActivity(), R.style.ButtonDefaultStyle);
                    if (Build.VERSION.SDK_INT >= 21) {
                        button.setBackground(getResources().getDrawable(R.drawable
                                .button_drawable, null));
                    } else if (Build.VERSION.SDK_INT >= 16) {
                        button.setBackground(getResources().getDrawable(R.drawable
                                .button_drawable));
                    } else {
                        button.setBackgroundResource(R.drawable.button_drawable);
                    }
                    button.setText("Print");
                    //button.setBackgroundResource(R.drawable.button_drawable);
                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup
                            .LayoutParams.WRAP_CONTENT, (int) getResources().getDimension(R.dimen
                            .button_height_default));
                    params.gravity = GravityCompat.END | Gravity.RIGHT;
                    params.rightMargin = 20;
                    params.topMargin = 10;
                    frameLayout.addView(button, params);
                    button.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            PrintManager printManager = (PrintManager) getActivity()
                                    .getSystemService(Context.PRINT_SERVICE);
                            final PrintDocumentAdapter printAdapter;

                            // Get a print adapter instance
                            String jobName = fileName + " Report";
                            if (Build.VERSION.SDK_INT >= 21) {
                                printAdapter = webView
                                        .createPrintDocumentAdapter(jobName);
                            } else if (Build.VERSION.SDK_INT >= 19) {
                                printAdapter = webView
                                        .createPrintDocumentAdapter();
                            } else {
                                printAdapter = null;
                            }

                            if (printAdapter != null && Build.VERSION.SDK_INT >= 19) {
                                // Create a print job with name and adapter instance
                                printManager.print(jobName, printAdapter,
                                        new PrintAttributes.Builder().build());
                            } else {
                                Toast.makeText(getActivity(), "Impossible to print because of " +
                                        "old api!", Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                }
            }
        });

        loadPpmCurve.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity().getBaseContext(), FileDialog.class);
                intent.putExtra(FileDialog.START_PATH, Constants.BASE_DIRECTORY.getAbsolutePath());
                intent.putExtra(FileDialog.ROOT_PATH, Constants.BASE_DIRECTORY.getAbsolutePath());
                intent.putExtra(FileDialog.SELECTION_MODE, SelectionMode.MODE_OPEN);

                IntentFolderWrapUtils.wrapFolderForDrawables(getActivity(), intent);

                intent.putExtra(FileDialog.FORMAT_FILTER, new String[]{"csv"});
                intent.putExtra(FileDialog.CAN_SELECT_DIR, true);
                intent.putExtra(FileDialog.FOLDER_SELECT_PROMPT, true);

                startActivityForResult(intent, LOAD_PPM_AVG_VALUES_REQUEST_CODE);
            }
        });

        graph1.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Activity activity = getActivity();

                if (ppmPoints.isEmpty()) {
                    Toast.makeText(getActivity(), "Please load CAL_Curve", Toast.LENGTH_LONG)
                            .show();
                    return;
                }

                List<Float> ppmPoints = new ArrayList<>();
                List<Float> avgSquarePoints = new ArrayList<>();
                ppmPoints.addAll(BottomFragment.this.ppmPoints);
                avgSquarePoints.addAll(BottomFragment.this.avgSquarePoints);

                ArrayList<String> ppmStrings = new ArrayList<>(ppmPoints.size());
                ArrayList<String> squareStrings = new ArrayList<>(avgSquarePoints.size());

                for (Float ppm : ppmPoints) {
                    ppmStrings.add(ppm.intValue() + "");
                }
                for (Float square : avgSquarePoints) {
                    squareStrings.add(FloatFormatter.format(square));
                }

                LibraryContentAttachable libraryContentAttachable = activity instanceof
                        LibraryContentAttachable ? (LibraryContentAttachable) activity : null;

                if (libraryContentAttachable != null) {
                    LinearLayout viewGroup = libraryContentAttachable.graphContainer();

                    LineChart lineChart = new LineChart(activity);

                    int countPoints = squareStrings.size();

                    SparseArray<Float> squares = new SparseArray<>(countPoints);

                    for (int i = 0; i < countPoints; i++) {
                        squares.put(Integer.parseInt(ppmStrings.get(i)), Float.parseFloat
                                (squareStrings.get
                                        (i)));
                    }

                    if (squares.size() < 2) {
                        Toast.makeText(getActivity(), "Please load Correct Calibration Curve!",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    CurveFragment.initLine(lineChart, activity, squares);

                    viewGroup.removeAllViews();

                    FrameLayout frameLayout = new FrameLayout(activity);

                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup
                            .LayoutParams.MATCH_PARENT, 60);
                    params.gravity = GravityCompat.END | Gravity.BOTTOM;

                    //View v1 = new View(activity);
                    //v1.setBackgroundColor(Color.BLACK);

                    //frameLayout.addView(v1, params);

                    LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams
                            (ViewGroup
                                    .LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams
                                    .MATCH_PARENT);
                    viewGroup.addView(frameLayout, containerParams);

                    frameLayout.addView(lineChart, new FrameLayout.LayoutParams(ViewGroup
                            .LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                    TextView textView = new TextView(activity, null, R.style.TextViewDefaultStyle);
                    textView.setTextColor(Color.BLACK);
                    textView.setText("ppm");

                    params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    params.gravity = GravityCompat.END | Gravity.BOTTOM;
                    params.rightMargin = 10;
                    params.bottomMargin = 20;
                    frameLayout.addView(textView, params);

                    textView = new TextView(activity, null, R.style.TextViewDefaultStyle);
                    textView.setTextColor(Color.BLACK);
                    textView.setText("SAV");

                    params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    params.leftMargin = 20;
                    params.topMargin = 20;
                    frameLayout.addView(textView, params);

                    libraryContentAttachable.onGraphAttached();

                    lineChart.invalidate();
                }
            }
        });

        mesSelectFolder.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity().getBaseContext(), FileDialog.class);

                File extFile = Environment.getExternalStorageDirectory();

                intent.putExtra(FileDialog.START_PATH, extFile.getAbsolutePath());
                intent.putExtra(FileDialog.ROOT_PATH, extFile.getAbsolutePath());
                intent.putExtra(FileDialog.SELECTION_MODE, SelectionMode.MODE_OPEN);

                intent.putExtra(FileDialog.MES_SELECTION_NAMES, new String[]{"CAL_FILES",
                        "MES_Files"});
                intent.putExtra(FileDialog.CAN_SELECT_DIR, true);

                IntentFolderWrapUtils.wrapFolderForDrawables(getActivity(), intent);

                startActivityForResult(intent, MES_SELECT_FOLDER);
            }
        });

        calculatePpmSimpleLoaded.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (avgValueLoaded.getText().toString().isEmpty()) {
                    Activity activity = getActivity();
                    Toast.makeText(activity, activity.getString(R.string.input_avg_value), Toast
                            .LENGTH_LONG).show();
                    return;
                }
                float avgValueY = Float.parseFloat(avgValueLoaded.getText().toString());
                float value;
                try {
                    List<Float> ppmPoints = new ArrayList<>();
                    List<Float> avgSquarePoints = new ArrayList<>();
                    ppmPoints.addAll(BottomFragment.this.ppmPoints);
                    avgSquarePoints.addAll(BottomFragment.this.avgSquarePoints);
                    value = CalculatePpmSimpleFragment.findPpmBySquare(avgValueY, ppmPoints,
                            avgSquarePoints);
                } catch (Exception e) {
                    value = -1;
                }

                if (value == -1) {
                    Activity activity = getActivity();
                    Toast.makeText(activity, activity.getString(R.string.wrong_data), Toast
                            .LENGTH_LONG).show();
                } else {
                    resultPpmLoaded.setText(FloatFormatter.format(value));
                }
            }
        });

        calculatePpmAuto.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                mDoPostLoadingCalculations = true;
                File calFolder = CalculatePpmSimpleFragment.findNewestFolder(Constants
                        .BASE_DIRECTORY, "CAL");

                Activity activity = getActivity();
                FrameLayout frameLayout = null;

                if (activity instanceof LibraryContentAttachable) {
                    frameLayout = ((LibraryContentAttachable) activity).getFrameLayout();
                }

                CreateCalibrationCurveForAutoTask task = new CreateCalibrationCurveForAutoTask(new
                        LoadGraphDataTask(activity, frameLayout, null, onGraphDataLoadedCallback),
                        getActivity(), true);
                //task.setIgnoreExistingCurves(true);
                task.execute(calFolder);
            }
        });

        mRealCalculationsCalculateAutoListener = new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (mAutoAvgPoint == null) {
                    Toast.makeText(getActivity(), "Average point not filled", Toast.LENGTH_LONG)
                            .show();
                    return;
                } else {
                    avgValueLoaded.setText(FloatFormatter.format(mAutoAvgPoint.avg()));
                }

                float avgValueY = Float.parseFloat(avgValueLoaded.getText().toString());
                float value;
                try {
                    List<Float> ppmPoints = new ArrayList<>();
                    List<Float> avgSquarePoints = new ArrayList<>();
                    ppmPoints.addAll(BottomFragment.this.ppmPoints);
                    avgSquarePoints.addAll(BottomFragment.this.avgSquarePoints);
                    value = CalculatePpmSimpleFragment.findPpmBySquare(avgValueY, ppmPoints,
                            avgSquarePoints);
                } catch (Exception e) {
                    value = -1;
                }

                if (value == -1) {
                    Activity activity = getActivity();
                    Toast.makeText(activity, activity.getString(R.string.wrong_data), Toast
                            .LENGTH_LONG).show();
                } else {
                    resultPpmLoaded.setText(FloatFormatter.format(value));
                }
            }
        };

        mClearRow2.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                resultPpmLoaded.setText("");
                avgValueLoaded.setText("");
                mAvgFiles = null;
                mCurveFile = null;
                ppmPoints.clear();
                avgSquarePoints.clear();
                fillAvgPointsLayout();
            }
        });

        initGraphDataLoadedCallback();

        InterpolationCalculatorApp interpolationCalculatorApp = InterpolationCalculatorApp
                .getInstance();
        if (interpolationCalculatorApp.getPpmPoints() != null) {
            ppmPoints = interpolationCalculatorApp.getPpmPoints();
            avgSquarePoints = interpolationCalculatorApp.getAvgSquarePoints();
            fillAvgPointsLayout();
        } else {
            ppmPoints = new ArrayList<>();
            avgSquarePoints = new ArrayList<>();
        }

        fillAvgPointsLayout();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(IS_SAVED, true);
        outState.putString(THIRD_TEXT_TAG, avgValueLoaded.getText().toString());
        outState.putString(FOURTH_TEXT_TAG, resultPpmLoaded.getText().toString());
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            avgValueLoaded.setText(savedInstanceState.getString(THIRD_TEXT_TAG));
            resultPpmLoaded.setText(savedInstanceState.getString(FOURTH_TEXT_TAG));
            fillAvgPointsLayout();
        }
    }

    /**
     * Fill layout with actual data.
     */
    private void fillAvgPointsLayout() {
        avgPointsLayout.removeAllViews();

        TextView tv;

        if (mCurveFile != null) {
            tv = new TextView(getActivity());
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen
                    .edit_text_size_default));
            tv.setText("Curve file: \"" + mCurveFile.getName() + "\"" + "   ");
            tv.setTextColor(Color.WHITE);
            avgPointsLayout.addView(tv);
        }

        if (mAvgFiles != null) {
            tv = new TextView(getActivity());
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen
                    .edit_text_size_default));
            tv.setText("Avg files: ");
            tv.setTextColor(Color.WHITE);
            avgPointsLayout.addView(tv);

            if (mAvgFiles.length == 1 || mAvgFiles.length <= 3) {

                //TODO it's remove to work only on auto calculations
                mAutoSelected = true;

                for (int i = 0; i < mAvgFiles.length - 1; i++) {
                    if (mAutoSelected) {
                        File file = mAvgFiles[i].getParentFile();
                        tv = new TextView(getActivity());
                        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R
                                .dimen
                                .edit_text_size_default));
                        tv.setText("\"" + file.getName() + "\":  ");
                        tv.setTextColor(Color.WHITE);

                        avgPointsLayout.addView(tv);
                    }

                    mAutoSelected = false;

                    File file = mAvgFiles[i];
                    tv = new TextView(getActivity());
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen
                            .edit_text_size_default));
                    tv.setText("\"" + file.getName() + "\",  ");
                    tv.setTextColor(Color.WHITE);

                    avgPointsLayout.addView(tv);
                }

                File file = mAvgFiles[mAvgFiles.length - 1];
                tv = new TextView(getActivity());
                tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen
                        .edit_text_size_default));
                tv.setText("\"" + file.getName() + "\"  ");
                tv.setTextColor(Color.WHITE);

                avgPointsLayout.addView(tv);
            } else {
                File parentFile = mAvgFiles[0].getParentFile();
                tv = new TextView(getActivity());
                tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen
                        .edit_text_size_default));
                tv.setText("Folder: \"" + parentFile.getName() + "\"" + "   ");
                tv.setTextColor(Color.WHITE);
                avgPointsLayout.addView(tv);
            }
        }

        if (mAvgFiles != null || mCurveFile != null) {
            tv = new TextView(getActivity());
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen
                    .edit_text_size_default));
            tv.setText("||  ");
            tv.setTextColor(Color.WHITE);
            avgPointsLayout.addView(tv);
        }

        if (!ppmPoints.isEmpty()) {
            for (int i = 0; i < ppmPoints.size(); i++) {
                tv = new TextView(getActivity());
                tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen
                        .edit_text_size_default));
                tv.setText(composePpmCurveText(Arrays.asList(ppmPoints.get(i)), Arrays
                        .asList(avgSquarePoints.get(i))));
                tv.setTextColor(Color.WHITE);

                avgPointsLayout.addView(tv);
            }
        }

        if (avgPointsLayout.getChildCount() == 0) {
            tv = new TextView(getActivity());
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen
                    .edit_text_size_default));
            tv.setText("");
            tv.setTextColor(Color.WHITE);
            avgPointsLayout.addView(tv);

        }

        calculatePpmLayoutLoaded.setVisibility(View.VISIBLE);
    }

    public static final String composePpmCurveText(List<Float> ppmPoints, List<Float>
            avgSquarePoints) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < ppmPoints.size(); i++) {
            builder.append(ppmPoints.get(i).intValue() + " " + FloatFormatter.format
                    (avgSquarePoints.get(i)) + "    ");
        }
        return builder.toString();
    }

    //load curve if press on folder -> then create calibration curve, as on auto load button.

    private void initGraphDataLoadedCallback() {
        onGraphDataLoadedCallback = new LoadGraphDataTask.OnGraphDataLoadedCallback() {

            @Override
            public void onGraphDataLoaded(List<Float> ppmValues, List<Float> avgSquareValues,
                                          String mMesFolder, String mUrl) {

                if (mUrl != null) {
                    mCurveFile = new File(mUrl);
                }

                if (mMesFolder != null) {
                    File mMesFolderFile = new File(mMesFolder);
                    final boolean isCorrectFilesSelected;
                    if (mMesFolderFile.isDirectory()) {
                        isCorrectFilesSelected = handleDirectoryMesSelected(searchCsvFilesInside
                                (mMesFolderFile));
                    } else {
                        isCorrectFilesSelected = handleCsvFileMesSelected(mMesFolderFile);
                    }
                    if (!isCorrectFilesSelected) {
                        Toast.makeText(getActivity(), "Wrong files for calculating", Toast
                                .LENGTH_LONG).show();
                    }

                    fillAvgPointsLayout();

                    return;
                }

                ppmPoints = ppmValues;
                avgSquarePoints = avgSquareValues;
                List<Float> ppmPoints = new ArrayList<>(BottomFragment
                        .this.ppmPoints);
                List<Float> avgSquarePoints = new ArrayList<>(BottomFragment
                        .this.avgSquarePoints);
                InterpolationCalculatorApp interpolationCalculatorApp = InterpolationCalculatorApp
                        .getInstance();
                interpolationCalculatorApp.setAvgSquarePoints(avgSquarePoints);
                interpolationCalculatorApp.setPpmPoints(ppmPoints);

                if (mDoPostLoadingCalculations) {
                    File mesFile = CalculatePpmSimpleFragment.findMesFile(Constants.BASE_DIRECTORY
                            .getParentFile());
                    if (mesFile != null && CalculatePpmSimpleFragment.findNewestFolder(mesFile,
                            "MES") != null) {
                        mesFile = CalculatePpmSimpleFragment.findNewestFolder(mesFile, "MES");
                        File mesFiles[] = mesFile.listFiles();
                        if (mesFiles == null && mesFile.getParentFile() != null) {
                            mesFiles = mesFile.getParentFile().listFiles();
                        } else if (mesFiles == null) {
                            Toast.makeText(getActivity(), "Wrong files for calculating", Toast
                                    .LENGTH_LONG).show();
                            return;
                        }
                        File newestCalFile1 = null, newestCalFile2 = null, newestCalFile3 = null;
                        for (File f : mesFiles) {
                            if (!f.isDirectory()) {
                                if (newestCalFile1 == null) {
                                    newestCalFile1 = f;
                                } else if (newestCalFile2 == null) {
                                    if (newestCalFile1.lastModified() > f.lastModified()) {
                                        newestCalFile2 = newestCalFile1;
                                        newestCalFile1 = f;
                                    } else {
                                        newestCalFile2 = f;
                                    }
                                } else if (newestCalFile3 == null) {
                                    if (newestCalFile2.lastModified() < f.lastModified()) {
                                        newestCalFile3 = f;
                                    } else if (newestCalFile1.lastModified() > f.lastModified()) {
                                        newestCalFile3 = newestCalFile2;
                                        newestCalFile2 = newestCalFile1;
                                        newestCalFile1 = f;
                                    } else {
                                        newestCalFile3 = newestCalFile2;
                                        newestCalFile2 = f;
                                    }
                                } else if (newestCalFile3.lastModified() > f.lastModified()) {
                                    if (newestCalFile2.lastModified() > f.lastModified()) {
                                        newestCalFile3 = f;
                                    } else if (newestCalFile1.lastModified() > f.lastModified()) {
                                        newestCalFile3 = newestCalFile2;
                                        newestCalFile2 = f;
                                    } else {
                                        newestCalFile3 = newestCalFile2;
                                        newestCalFile2 = newestCalFile1;
                                        newestCalFile1 = f;
                                    }
                                }
                            }
                        }

                        if (newestCalFile1 != null) {
                            float square1 = CalculateUtils.calculateSquare(newestCalFile1);
                            if (square1 == -1) {
                                Toast.makeText(getActivity(), "Wrong files for calculating", Toast
                                        .LENGTH_LONG).show();
                                return;
                            } else {
                                if (newestCalFile2 == null) {
                                    mAvgFiles = new File[]{newestCalFile1};
                                    mAutoSelected = true;
                                    mAutoAvgPoint = new AvgPoint(Arrays.asList(new
                                            Float[]{square1}));
                                    fillAvgPointsLayout();
                                    mRealCalculationsCalculateAutoListener.onClick(null);
                                    //mClearRow2.performClick();
                                    return;
                                }
                                float square2 = CalculateUtils.calculateSquare(newestCalFile2);
                                if (square2 == -1) {
                                    Toast.makeText(getActivity(), "Wrong files for calculating",
                                            Toast.LENGTH_LONG).show();
                                    return;
                                } else {
                                    if (newestCalFile3 == null) {
                                        mAvgFiles = new File[]{newestCalFile1, newestCalFile2};
                                        mAutoSelected = true;
                                        mAutoAvgPoint = new AvgPoint(Arrays.asList(new
                                                Float[]{square1, square2}));
                                        fillAvgPointsLayout();
                                        mRealCalculationsCalculateAutoListener.onClick(null);
                                        //mClearRow2.performClick();
                                        return;
                                    }
                                    float square3 = CalculateUtils.calculateSquare(newestCalFile3);
                                    if (square3 == -1) {
                                        Toast.makeText(getActivity(), "Wrong files for " +
                                                "calculating", Toast.LENGTH_LONG).show();
                                        return;
                                    } else {
                                        mAvgFiles = new File[]{newestCalFile1, newestCalFile2,
                                                newestCalFile3};
                                        mAutoSelected = true;
                                        mAutoAvgPoint = new AvgPoint(Arrays.asList(new
                                                Float[]{square1, square2, square3}));
                                        fillAvgPointsLayout();
                                        mRealCalculationsCalculateAutoListener.onClick(null);
                                        //mClearRow2.performClick();
                                    }
                                }
                            }
                        }
                    } else {
                        Toast.makeText(getActivity(), "Please make MES directory to find ppm",
                                Toast.LENGTH_LONG).show();
                    }
                    mDoPostLoadingCalculations = false;
                } else {
                    fillAvgPointsLayout();
                }
            }
        };
    }

    private boolean handleDirectoryMesSelected(List<File> files) {
        List<Float> correctSquares = new ArrayList<>(files.size());
        List<File> correctFiles = new ArrayList<>();
        for (File file : files) {
            float square1 = CalculateUtils.calculateSquare(file);
            if (square1 > 0) {
                correctSquares.add(square1);
                correctFiles.add(file);
            }
        }
        if (correctSquares.isEmpty()) {
            mAvgFiles = new File[]{};
            return false;
        }

        mAutoAvgPoint = new AvgPoint(correctSquares);
        avgValueLoaded.setText(FloatFormatter.format(mAutoAvgPoint.avg()));

        mAvgFiles = new File[correctFiles.size()];
        correctFiles.toArray(mAvgFiles);

        return true;
    }

    private boolean handleCsvFileMesSelected(File csvFile) {
        final float square1 = CalculateUtils.calculateSquare(csvFile);
        if (square1 > 0) {
            mAutoAvgPoint = new AvgPoint(new ArrayList<Float>() {{
                add(square1);
            }});
            avgValueLoaded.setText(FloatFormatter.format(mAutoAvgPoint.avg()));
        }
        mAvgFiles = new File[]{csvFile};

        return square1 > 0;
    }

    private List<File> searchCsvFilesInside(final File file) {
        if (!file.isDirectory()) {
            if (!file.getAbsolutePath().endsWith(".csv")) {
                return null;
            } else {
                return new ArrayList<File>() {{
                    add(file);
                }};
            }
        } else {
            List<File> result = new ArrayList<>();
            for (File localFile : file.listFiles()) {
                List<File> filesInside = searchCsvFilesInside(localFile);
                if (filesInside != null) {
                    result.addAll(filesInside);
                }
            }
            return result;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            resultPpmLoaded.setText("");
            switch (requestCode) {
                case LOAD_PPM_AVG_VALUES_REQUEST_CODE:
                    mDoPostLoadingCalculations = false;
                    Activity activity = getActivity();
                    FrameLayout frameLayout = null;
                    if (activity instanceof LibraryContentAttachable) {
                        frameLayout = ((LibraryContentAttachable) activity).getFrameLayout();
                    }
                    new LoadGraphDataTask(getActivity(), frameLayout, data.getStringExtra
                            (FileDialog.RESULT_PATH), onGraphDataLoadedCallback).execute();
                    break;
                case MES_SELECT_FOLDER:
                    mDoPostLoadingCalculations = true;
                    onGraphDataLoadedCallback.onGraphDataLoaded(null, null, data.getStringExtra
                            (FileDialog.RESULT_PATH), null);
                    break;
            }
        }
    }
}
