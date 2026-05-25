package modules.userContext.services;

import modules.userContext.structures.MacroCurve;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

import java.time.LocalTime;

public final class CurveGenerator {
    private CurveGenerator() {
    }

    public static MacroCurve createSmoothCurve(double[] timePointsX, double[] valuesY) {
        if (timePointsX == null || valuesY == null) {
            throw new IllegalArgumentException("timePointsX and valuesY must not be null");
        }
        if (timePointsX.length != valuesY.length) {
            throw new IllegalArgumentException("timePointsX and valuesY must have equal length");
        }
        if (timePointsX.length < 3) {
            throw new IllegalArgumentException("At least three points are required for spline interpolation");
        }

        PolynomialSplineFunction splineFunction = new SplineInterpolator().interpolate(timePointsX, valuesY);
        double firstX = timePointsX[0];
        double lastX = timePointsX[timePointsX.length - 1];
        double firstY = valuesY[0];
        double lastY = valuesY[valuesY.length - 1];
        boolean crossesMidnight = lastX > 24.0;

        return (LocalTime time) -> {
            double t = time.getHour() + (time.getMinute() / 60.0) + (time.getSecond() / 3600.0);
            if (crossesMidnight && t < firstX) {
                t += 24.0;
            }

            if (t <= firstX) {
                return firstY;
            }
            if (t >= lastX) {
                return lastY;
            }
            return splineFunction.value(t);
        };
    }
}
