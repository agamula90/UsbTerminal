package com.proggroup.areasquarecalculator.fragments;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.lamerman.FileDialog;
import com.lamerman.SelectionMode;
import com.proggroup.areasquarecalculator.InterpolationCalculator;
import com.proggroup.areasquarecalculator.R;
import com.proggroup.areasquarecalculator.api.LibraryContentAttachable;
import com.proggroup.areasquarecalculator.data.AvgPoint;
import com.proggroup.areasquarecalculator.data.Constants;
import com.proggroup.areasquarecalculator.tasks.CreateCalibrationCurveForAutoTask;
import com.proggroup.areasquarecalculator.utils.FloatFormatter;
import com.proggroup.squarecalculations.CalculateUtils;

import java.io.File;
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

    private View loadPpmCurve;
    private View graph1;
    private View mesSelectFolder;
    private View calculatePpmLayoutLoaded;
    private EditText avgValueLoaded;
    private View calculatePpmSimpleLoaded, calculatePpmAuto;
    private TextView resultPpmLoaded;
    private View mClearRow2;
    private LinearLayout avgPointsLayout;

    private static Bundle sBundle = new Bundle();

    private List<Float> ppmPoints;
    private List<Float> avgSquarePoints;

    private List<Float> ppmAutoPoints;
    private List<Float> avgSquaresAutoPoints;

    private AvgPoint mAutoAvgPoint;

    private boolean mDoPostLoadingCalculations;
    private String mUrlWhenAutoLoading;

    private LoadGraphDataTask.OnGraphDataLoadedCallback onGraphDataLoadedCallback;
    private View.OnClickListener mRealCalculationsCalculateAutoListener;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable
    Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_bottom, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
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
        avgPointsLayout = (LinearLayout) view.findViewById(R.id.avg_points);

        loadPpmCurve.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity().getBaseContext(), FileDialog
                        .class);
                intent.putExtra(FileDialog.START_PATH, Constants.BASE_DIRECTORY
                        .getAbsolutePath());
                intent.putExtra(FileDialog.ROOT_PATH, Constants.BASE_DIRECTORY
                        .getAbsolutePath());
                intent.putExtra(FileDialog.SELECTION_MODE, SelectionMode.MODE_OPEN);

                intent.putExtra(FileDialog.FORMAT_FILTER, new String[]{"csv"});
                startActivityForResult(intent, LOAD_PPM_AVG_VALUES_REQUEST_CODE);
            }
        });

        graph1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Activity activity = getActivity();

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
                    FragmentManager fragmentManager = libraryContentAttachable
                            .getSupportFragmentManager();

                    int fragmentContainerId = libraryContentAttachable.getFragmentContainerId();

                    FragmentTransaction transaction = fragmentManager.beginTransaction();
                    transaction.replace(fragmentContainerId, CurveFragment.newInstance(ppmStrings,
                            squareStrings));
                    transaction.addToBackStack(null);
                    transaction.commit();
                }
            }
        });

        mesSelectFolder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity().getBaseContext(), FileDialog
                        .class);

                File extFile = Environment.getExternalStorageDirectory();

                intent.putExtra(FileDialog.START_PATH, extFile.getAbsolutePath());
                intent.putExtra(FileDialog.ROOT_PATH, extFile.getAbsolutePath());
                intent.putExtra(FileDialog.SELECTION_MODE, SelectionMode.MODE_OPEN);

                intent.putExtra(FileDialog.MES_SELECTION_NAMES, new String[]{"CAL_FILES",
                        "MES_Files"});
                intent.putExtra(FileDialog.CAN_SELECT_DIR, true);

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
                File calFolder = CalculatePpmSimpleFragment.findCalFolder(Constants.BASE_DIRECTORY);

                new CreateCalibrationCurveForAutoTask(new LoadGraphDataTask(getActivity(), null,
                        onGraphDataLoadedCallback), getActivity(), true).execute(calFolder);
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
                    ppmAutoPoints = new ArrayList<>(ppmPoints);
                    avgSquaresAutoPoints = new ArrayList<Float>(avgSquarePoints);
                }

                float avgValueY = Float.parseFloat(avgValueLoaded.getText().toString());
                float value;
                try {
                    List<Float> ppmPoints = new ArrayList<>();
                    List<Float> avgSquarePoints = new ArrayList<>();
                    ppmPoints.addAll(ppmAutoPoints);
                    avgSquarePoints.addAll(avgSquaresAutoPoints);
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
            }
        });

        initGraphDataLoadedCallback();

        TextView tv = new TextView(getActivity());
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen
                .edit_text_size_default));
        tv.setText("");
        tv.setTextColor(Color.WHITE);
        avgPointsLayout.addView(tv);
        graph1.setVisibility(View.GONE);

        InterpolationCalculator interpolationCalculator = InterpolationCalculator.getInstance();
        if (interpolationCalculator.getPpmPoints() != null) {
            ppmPoints = interpolationCalculator.getPpmPoints();
            avgSquarePoints = interpolationCalculator.getAvgSquarePoints();
            fillAvgPointsLayout();
            graph1.setVisibility(View.VISIBLE);
        }

        File calFolder = CalculatePpmSimpleFragment.findCalFolder(Constants.BASE_DIRECTORY);

        ppmPoints = new ArrayList<>();
        avgSquarePoints = new ArrayList<>();

        boolean wasSaved = sBundle.getBoolean(IS_SAVED, false);

        if (wasSaved) {
            savedInstanceState = sBundle;
            avgValueLoaded.setText(savedInstanceState.getString(THIRD_TEXT_TAG));
            resultPpmLoaded.setText(savedInstanceState.getString(FOURTH_TEXT_TAG));
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

    /**
     * Fill layout with actual data.
     */
    private void fillAvgPointsLayout() {
        avgPointsLayout.removeAllViews();

        for (int i = 0; i < ppmPoints.size(); i++) {
            TextView tv = new TextView(getActivity());
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen
                    .edit_text_size_default));
            tv.setText(ppmPoints.get(i).intValue() + " " + FloatFormatter.format
                    (avgSquarePoints.get(i)) + "    ");
            tv.setTextColor(Color.WHITE);

            avgPointsLayout.addView(tv);
        }
        calculatePpmLayoutLoaded.setVisibility(View.VISIBLE);
    }

    private void initGraphDataLoadedCallback() {
        onGraphDataLoadedCallback = new LoadGraphDataTask.OnGraphDataLoadedCallback() {
            @Override
            public void onGraphDataLoaded(List<Float> ppmValues, List<Float> avgSquareValues,
                                          boolean isAutoLoading, String mMesFolder, String mUrl) {
                ppmPoints = ppmValues;
                avgSquarePoints = avgSquareValues;
                fillAvgPointsLayout();
                List<Float> ppmPoints = new ArrayList<>(BottomFragment
                        .this.ppmPoints);
                List<Float> avgSquarePoints = new ArrayList<>(BottomFragment
                        .this.avgSquarePoints);
                InterpolationCalculator interpolationCalculator =
                        InterpolationCalculator.getInstance();
                interpolationCalculator.setAvgSquarePoints(avgSquarePoints);
                interpolationCalculator.setPpmPoints(ppmPoints);
                graph1.setVisibility(View.VISIBLE);

                if (mDoPostLoadingCalculations) {
                    mUrlWhenAutoLoading = mUrl;
                    File mesFile = null;

                    if (mMesFolder == null) {
                        mesFile = CalculatePpmSimpleFragment.findMesFile(Constants.BASE_DIRECTORY
                                .getParentFile());
                    }
                    if (mMesFolder != null || (mesFile != null && CalculatePpmSimpleFragment
                            .findMesFile(mesFile) != null)) {
                        if (mMesFolder != null) {
                            File mMesFolderFile = new File(mMesFolder);

                            final boolean isCorrectFilesSelected;
                            if (mMesFolderFile.isDirectory()) {
                                isCorrectFilesSelected = handleDirectoryMesSelected
                                        (searchCsvFilesInside(mMesFolderFile));
                            } else {
                                isCorrectFilesSelected = handleCsvFileMesSelected(mMesFolderFile);
                            }
                            if (!isCorrectFilesSelected) {
                                Toast.makeText(getActivity(), "Wrong files for calculating", Toast
                                        .LENGTH_LONG).show();
                            }
                            return;
                        }

                        mesFile = CalculatePpmSimpleFragment.findMesFile(mesFile);
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
                                    mAutoAvgPoint = new AvgPoint(Arrays
                                            .asList(new Float[]{square1}));
                                    mRealCalculationsCalculateAutoListener.onClick(null);
                                    //mClearRow2.performClick();
                                    return;
                                }
                                float square2 = CalculateUtils.calculateSquare(newestCalFile2);
                                if (square2 == -1) {
                                    Toast.makeText(getActivity(), "Wrong files for calculating",
                                            Toast
                                                    .LENGTH_LONG).show();
                                    return;
                                } else {
                                    if (newestCalFile3 == null) {
                                        mAutoAvgPoint = new AvgPoint(Arrays
                                                .asList(new Float[]{square1, square2}));
                                        mRealCalculationsCalculateAutoListener.onClick(null);
                                        //mClearRow2.performClick();
                                        return;
                                    }
                                    float square3 = CalculateUtils.calculateSquare(newestCalFile3);
                                    if (square3 == -1) {
                                        Toast.makeText(getActivity(), "Wrong files for calculating",
                                                Toast
                                                        .LENGTH_LONG).show();
                                        return;
                                    } else {
                                        mAutoAvgPoint = new AvgPoint(Arrays
                                                .asList(new Float[]
                                                        {square1, square2, square3}));
                                        mRealCalculationsCalculateAutoListener.onClick(null);
                                        //mClearRow2.performClick();
                                    }
                                }
                            }
                        }
                    } else {
                        Toast.makeText(getActivity(), "Please make MES directory to find ppm", Toast
                                .LENGTH_LONG).show();
                    }
                    mDoPostLoadingCalculations = false;
                }
            }
        };
    }

    private boolean handleDirectoryMesSelected(List<File> files) {
        List<Float> correctSquares = new ArrayList<>(files.size());
        for (File file : files) {
            float square1 = CalculateUtils.calculateSquare(file);
            if (square1 > 0) {
                correctSquares.add(square1);
            }
        }
        if (correctSquares.isEmpty()) {
            return false;
        }

        mAutoAvgPoint = new AvgPoint(correctSquares);
        avgValueLoaded.setText(FloatFormatter.format(mAutoAvgPoint.avg()));
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
            switch (requestCode) {
                case LOAD_PPM_AVG_VALUES_REQUEST_CODE:
                    new LoadGraphDataTask(getActivity(), data.getStringExtra(FileDialog
                            .RESULT_PATH), onGraphDataLoadedCallback).execute();
                    break;
                case MES_SELECT_FOLDER:
                    mDoPostLoadingCalculations = true;
                    LoadGraphDataTask task = new LoadGraphDataTask(getActivity(),
                             mUrlWhenAutoLoading, onGraphDataLoadedCallback);
                    task.setMesFolder(data.getStringExtra(FileDialog.RESULT_PATH));
                    task.execute();
                    break;
            }
        }
    }
}
