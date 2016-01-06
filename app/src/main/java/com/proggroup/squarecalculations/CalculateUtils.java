package com.proggroup.squarecalculations;

import android.graphics.PointF;

import org.ejml.factory.SingularMatrixException;
import org.ejml.simple.SimpleMatrix;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CalculateUtils {

	/**
	 * Eps of detect if we can solve a*x*x + b*x + c = y equation or no.
	 */
	private static final float EPS = 1e-8f;

	private static List<Float> squareSums = new ArrayList<>();

	/**
	 * Search (a, b, c) params of equation a*x*x + b*x + c = y.
	 *
	 * @param powMatrix This will be matrix (x*x, x, 1) for each of known x (3*3 matrix)
	 * @param y         Y known values, for which we search solution.
	 * @return (a, b, c) params
	 */
	private static List<Float> calculateApproximateParams(SimpleMatrix powMatrix, List<Float> y) {
		int size = y.size();

		double yValues[][] = new double[size][1];
		for (int j = 0; j < size; j++) {
			yValues[j][0] = y.get(j);
		}

		SimpleMatrix yMatrix = new SimpleMatrix(yValues);
		SimpleMatrix invert = powMatrix.invert();

		SimpleMatrix paramsMatrix = invert.mult(yMatrix);

		int countRows = paramsMatrix.numRows();

		List<Float> res = new ArrayList<>(countRows);
		for (int i = 0; i < countRows; i++) {
			res.add((float) paramsMatrix.get(i, 0));
		}

		return res;
	}

	/**
	 * Return pow matrix for x values.
	 *
	 * @param x Known x values,
	 * @return Pow matrix (x*x, x, 1) for each of x
	 */
	private static SimpleMatrix calculatePowMatrix(List<Float> x) {
		int size = x.size();
		double xValues[][] = new double[size][size];
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				xValues[i][j] = pow(x.get(i), size - 1 - j);
			}
		}
		return new SimpleMatrix(xValues);
	}

	/**
	 * Detect if we can find invert matrix for param matrix.
	 *
	 * @param matrix Matrix for detect if we can find solution of 2-power equation or no.
	 * @return Result: true - for success, false - for fail.
	 */
	private static boolean canInvert(SimpleMatrix matrix) {
		double mathDet = Math.abs(matrix.determinant());
		return mathDet >= EPS;
	}

	/**
	 * Find square between curve, and yCached value. Curve - approximated as line.
	 *
	 * @param xFrom   From value by x - to calculate square from.
	 * @param yFrom   From value by y - to calculate square from.
	 * @param xTo     To value by x - to calculate square from.
	 * @param yTo     To value by y - to calculate square from.
	 * @param yCached Cached value by y, to subtract area from 0 to yCached.
	 * @return Result square of region.
	 */
	private static double calculateTrapezeSquare(float xFrom, float yFrom, float xTo, float yTo,
			float yCached) {
		return ((yFrom + yTo) / 2) * (xTo - xFrom) - yCached * (xTo - xFrom);
	}

	/**
	 * Return square below the curve a_n*x^n + a_(n-1)*x^(n-1) + ... + a_1*x + a_0 = y.
	 * n - count of funcParams values - 1.
	 *
	 * @param funcParams Calculated by method calculateApproximateParams values (a, b, c)
	 * @param xFrom      From value by x - to calculate square from.
	 * @param xTo        To value by x - to calculate square from.
	 * @param yCached    Cached value by y, to subtract area from 0 to yCached.
	 * @return Result square of region.
	 */
	private static float calculateSquare(List<Float> funcParams, Float xFrom, Float xTo, float
			yCached) {
		int size = funcParams.size();

		float squareSize = 0f;

		for (int i = 0; i < size; i++) {
			int curPower = size - i;
			squareSize += funcParams.get(i) * (pow(xFrom, curPower) - pow(xTo, curPower)) /
					curPower;
		}

		return Math.abs(squareSize) - (xTo - xFrom) * yCached;
	}

	/**
	 * Speed calculations of val^power.
	 *
	 * @param val   Value for power.
	 * @param power Degree for which we'll bring to.
	 * @return Result of val^power.
	 */
	private static float pow(float val, int power) {
		if (power == 0) {
			return 1;
		} else if (power == 1) {
			return val;
		} else {
			float value_2 = pow(val, power / 2);
			return value_2 * value_2 * (power % 2 == 0 ? 1 : val);
		}
	}

	/**
	 * Search of square, using determinant for approximation. Data loaded from file.
	 *
	 * @param file File, from which values will be loaded.
	 * @return Result square.
	 */
	public static float calculateSquareDeterminant(File file) {
		List<PointF> points = DocParser.parse(file);

		return calculateSquareDeterminant(points);
	}

	/**
	 * Search of square, using determinant for approximation. Data is pulled as param.
	 *
	 * @param points Param, used for square calculation.
	 * @return Result square.
	 */
	public static float calculateSquareDeterminant(List<PointF> points) {

		int startIndex = findStartIndex(points);

		float yCached = points.get(startIndex).y;

		if (startIndex != -1) {
			int endIndex = findEndIndex(points, points.get(startIndex).y);

			points = new ArrayList<>(points.subList(startIndex, endIndex + 1));
		} else {
			return -1;
		}

		int len = points.size();

		float sum = 0;

		for (int i = 0; i < len; ) {
			if (i <= len - 3) {
				List<Float> interpolateXs = new ArrayList<>(3);

				float xStart = points.get(i).x;
				float xEnd = points.get(i + 2).x;

				interpolateXs.add(points.get(i).x);
				interpolateXs.add(points.get(i + 1).x);
				interpolateXs.add(points.get(i + 2).x);
				SimpleMatrix powMatrix = CalculateUtils.calculatePowMatrix(interpolateXs);

				List<Float> interpolateYs = new ArrayList<>(3);
				interpolateYs.add(points.get(i).y);
				interpolateYs.add(points.get(i + 1).y);
				interpolateYs.add(points.get(i + 2).y);

				//TODO simulate only interpolation by linear function, not quad
				if (canInvert(powMatrix) && false) {
					List<Float> params = CalculateUtils.calculateApproximateParams(powMatrix,
							interpolateYs);
					sum += CalculateUtils.calculateSquare(params, xStart, xEnd, yCached);
					i += 2;
				} else {
					float yStart = points.get(i).y;
					float yEnd = points.get(i + 1).y;
					xEnd = points.get(i + 1).x;
					sum += CalculateUtils.calculateTrapezeSquare(xStart, yStart, xEnd, yEnd,
							yCached);
					i += 1;
				}
			} else if (i == len - 2) {
				sum += CalculateUtils.calculateTrapezeSquare(points.get(i).x, points.get(i).y,
						points.get(i + 1).x, points.get(i + 1).y, yCached);
				i += 2;
			} else {
				i++;
			}
		}

		return sum;
	}

	/**
	 * Search of square, using determinant for approximation. Data loaded from file. Parallel
	 * execution.
	 *
	 * @param file File, from which values will be loaded.
	 * @return Result square.
	 */
	public static float calculateSquareDeterminantParallel(File file) {
		List<PointF> points = DocParser.parse(file);

		return calculateSquareDeterminantParallel(points);
	}

	/**
	 * Search of square, using determinant for approximation. Data is pulled as param. Parallel
	 * execution.
	 *
	 * @param points Param, used for square calculation.
	 * @return Result square.
	 */
	public static float calculateSquareDeterminantParallel(List<PointF> points) {

		int startIndex = findStartIndex(points);

		float yCached = points.get(startIndex).y;

		if (startIndex != -1) {
			int endIndex = findEndIndex(points, yCached);

			points = new ArrayList<>(points.subList(startIndex, endIndex + 1));
		} else {
			return -1f;
		}

		int len = points.size();

		List<SimpleMatrix> simpleMatrices = new ArrayList<>();
		List<Integer> delIndexes = new ArrayList<>();
		delIndexes.add(0);

		squareSums.clear();

		List<List<PointF>> squareCalcPoints;

		squareCalcPoints = new ArrayList<>();

		int countDelims = delIndexes.size();
		int lastDelim = delIndexes.get(countDelims - 1);
		boolean i_not_eq_len_2 = true;

		for (int i = 0; i < len; ) {
			boolean changed = true;
			if (i <= len - 3) {
				List<Float> interpolateXs = new ArrayList<>(3);
				interpolateXs.add(points.get(i).x);
				interpolateXs.add(points.get(i + 1).x);
				interpolateXs.add(points.get(i + 2).x);

				SimpleMatrix powMatrix = CalculateUtils.calculatePowMatrix(interpolateXs);

				if (canInvert(powMatrix) && false) {
					simpleMatrices.add(powMatrix);
					i += 2;
					delIndexes.add(i);
				} else {
					i += 1;
					delIndexes.add(i);
				}
			} else if (i == len - 2) {
				i += 2;
				delIndexes.add(i - 1);
				i_not_eq_len_2 = false;
			} else {
				changed = false;
				i++;
			}

			if (changed) {
				if (i_not_eq_len_2) {
					squareCalcPoints.add(points.subList(lastDelim, i + 1));
					lastDelim = i;
				} else {
					squareCalcPoints.add(points.subList(lastDelim, i));
					lastDelim = i - 1;
				}
				squareSums.add(null);
			}

		}

		ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime()
				.availableProcessors());

		int k = 0;

		CountDownLatch latch = new CountDownLatch(delIndexes.size() - 1);

		for (int i = 1; i < delIndexes.size(); i++) {
			final SimpleMatrix powMatrix;


			if (delIndexes.get(i) - delIndexes.get(i - 1) == 2) {
				powMatrix = simpleMatrices.get(k++);
			} else {
				powMatrix = null;
			}
			service.execute(new CalculationRunnable(squareCalcPoints.get(i - 1), powMatrix,
					yCached, latch, i - 1));
		}
		try {
			latch.await();

			float res = 0f;
			for (Float square : squareSums) {
				res += square;
			}

			service.shutdown();

			return res;

		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		return 0f;
	}

	/**
	 * Search of first index of growing y.
	 *
	 * @param valPoints Y points, first index is searched from.
	 * @return Index of first growing value.
	 */
	private static int findStartIndex(List<PointF> valPoints) {
		for (int i = 0; i < valPoints.size() - 1; i++) {
			if (valPoints.get(i).y < valPoints.get(i + 1).y) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Search of last index of growing y.
	 *
	 * @param valPoints        Y points, last index is searched from.
	 * @param cachedStartValue Cached value - search can be stopped, when we reach this value.
	 * @return Index of last growing value.
	 */
	private static int findEndIndex(List<PointF> valPoints, float cachedStartValue) {
		int len = valPoints.size();
		if (valPoints.get(len - 1).y > cachedStartValue) {
			valPoints.get(len - 1).y = cachedStartValue;
		}
		for (int i = len - 1; i >= 0; i--) {
			if (valPoints.get(i).y == cachedStartValue || (i < len - 1 && valPoints.get(i).y <
					valPoints.get(i + 1).y)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Search of square, using java exception handling way. Data loaded from file.
	 *
	 * @param file File, from which values will be loaded.
	 * @return Result square.
	 */
	public static float calculateSquare(File file) {
		List<PointF> points = DocParser.parse(file);

		if (points.isEmpty()) {
			return -1f;
		}
		return calculateSquare(points);
	}

	/**
	 * Search of square, using java exception handling way. Data is pulled as param.
	 *
	 * @param points Param, used for square calculation.
	 * @return Result square.
	 */
	public static float calculateSquare(List<PointF> points) {

		int startIndex = findStartIndex(points);

		float yCached = points.get(startIndex).y;

		if (startIndex != -1) {
			int endIndex = findEndIndex(points, yCached);

			points = new ArrayList<>(points.subList(startIndex, endIndex + 1));
		} else {
			return -1;
		}

		int len = points.size();

		float sum = 0;

		for (int i = 0; i < len; ) {
			if (i <= len - 3) {
				List<Float> interpolateXs = new ArrayList<>(3);

				float xStart = points.get(i).x;
				float xEnd = points.get(i + 2).x;

				interpolateXs.add(points.get(i).x);
				interpolateXs.add(points.get(i + 1).x);
				interpolateXs.add(points.get(i + 2).x);
				SimpleMatrix powMatrix = CalculateUtils.calculatePowMatrix(interpolateXs);

				List<Float> interpolateYs = new ArrayList<>(3);
				interpolateYs.add(points.get(i).y);
				interpolateYs.add(points.get(i + 1).y);
				interpolateYs.add(points.get(i + 2).y);

				try {
					//TODO simulate only interpolation by linear function, not quad
					if (true) {
						throw new SingularMatrixException();
					}
					List<Float> params = CalculateUtils.calculateApproximateParams(powMatrix,
							interpolateYs);
					sum += CalculateUtils.calculateSquare(params, xStart, xEnd, yCached);
					i += 2;
				} catch (SingularMatrixException e) {
					float yStart = points.get(i).y;
					float yEnd = points.get(i + 1).y;
					xEnd = points.get(i + 1).x;
					sum += CalculateUtils.calculateTrapezeSquare(xStart, yStart, xEnd, yEnd,
							yCached);
					i += 1;
				}
			} else if (i == len - 2) {
				sum += CalculateUtils.calculateTrapezeSquare(points.get(i).x, points.get(i).y,
						points.get(i + 1).x, points.get(i + 1).y, yCached);
				i += 2;
			} else {
				i++;
			}
		}

		return sum;
	}

	interface OnCalculatingFinishedListener {

		void onCalculatingFinished(float value);
	}

	private static class CalculationRunnable implements Runnable {

		private final List<PointF> squarePoints;

		private final SimpleMatrix powMatrix;

		private final float yCached;

		private final CountDownLatch latch;

		private final int position;

		CalculationRunnable(List<PointF> squarePoints, SimpleMatrix powMatrix, float yCached,
				CountDownLatch latch, int position) {
			this.squarePoints = squarePoints;
			this.powMatrix = powMatrix;
			this.yCached = yCached;
			this.latch = latch;
			this.position = position;
		}

		@Override
		public void run() {
			final float calculatedSquareOne;
			if (powMatrix != null) {
				List<Float> yValues = new ArrayList<>();
				for (PointF squarePoint : squarePoints) {
					yValues.add(squarePoint.y);
				}
				List<Float> params = calculateApproximateParams(powMatrix, yValues);
				calculatedSquareOne = calculateSquare(params, squarePoints.get(0).x, squarePoints
						.get(squarePoints.size() - 1).x, yCached);
			} else {
				int size = squarePoints.size();

				calculatedSquareOne = (float) CalculateUtils.calculateTrapezeSquare(squarePoints
						.get(0).x, squarePoints.get(0).y, squarePoints.get(size - 1).x,
						squarePoints.get(size - 1).y, yCached);
			}

			squareSums.set(position, calculatedSquareOne);

			latch.countDown();
		}
	}
}
