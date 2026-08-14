package tankbase;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.lang.Math.PI;
import static java.lang.Math.abs;
import static java.lang.Math.acos;
import static java.lang.Math.asin;
import static java.lang.Math.cos;
import static java.lang.Math.exp;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.pow;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static robocode.Rules.MAX_VELOCITY;
import static robocode.util.Utils.normalRelativeAngle;
import static tankbase.AbstractTankBase.FIELD_HEIGHT;
import static tankbase.AbstractTankBase.FIELD_WIDTH;
import static tankbase.Constant.TANK_SIZE;

public class TankUtils {

    private TankUtils() {
    }

    // -PI -> PI
    public static double getPointAngle(Point2D.Double a, Point2D.Double b) {
        double angle = acos((b.x - a.x) / a.distance(b));
        if (b.y < a.y)
            angle = 2 * PI - angle;
        return normalRelativeAngle(angle);
    }

    // Return angle of s vertex 0-> PI
    public static double getVertexAngle(Point2D.Double s, Point2D.Double a, Point2D.Double b) {
        double ab = a.distance(b);
        double sa = a.distance(s);
        double sb = b.distance(s);

        if (ab == 0 || sa == 0 || sb == 0) return 0;

        return acos(max(-1, min(1, (pow(sa, 2) + pow(sb, 2) - pow(ab, 2)) / 2 / sa / sb)));
    }

    // -PI -> PI
    public static double oppositeAngle(double a) {
        return normalRelativeAngle(a + PI);
    }

    // convert robocode angle to trigonometric angle -PI -> PI
    public static double trigoAngle(double roboAngle) {
        return normalRelativeAngle(PI / 2 - roboAngle);
    }

    public static double checkMinMax(double v, double min, double max) {
        return max(min, min(max, v));
    }

    public static double range(double v, double minv, double maxv, double minr, double maxr) {
        if (minv == maxv) return minr;
        return (maxr > minr)
                ? (v - minv) / (maxv - minv) * (maxr - minr) + minr
                : (maxv - v) / (maxv - minv) * (minr - maxr) + maxr;
    }

    public static Point2D.Double clonePoint(Point2D.Double p) {
        return new Point2D.Double(p.getX(), p.getY());
    }

    public static double maximumEscapeAngle(double vb) {
        return asin(MAX_VELOCITY / vb);
    }

    public static Point2D.Double middle(Point2D.Double a, Point2D.Double b) {
        return middle(a, b, 1, 1);
    }

    public static Point2D.Double middle(Point2D.Double a, Point2D.Double b, int ac, int bc) {
        return new Point2D.Double((a.getX() * ac + b.getX() * bc) / (ac + bc), (a.getY() * ac + b.getY() * bc) / (ac + bc));
    }

    public static Point2D.Double middle(Point2D.Double a, Point2D.Double b, double ratio) {
        return new Point2D.Double(a.getX() + (b.getX() - a.getX()) * ratio, a.getY() + (b.getY() - a.getY()) * ratio);
    }

    public static double computeTurnGun2Target(Point2D.Double base, Point2D.Double target, double ga) {
        double ta = getPointAngle(base, target);
        return (abs(ta - ga) <= PI) ? ta - ga : ga - ta;
    }

    public static double computeTurnGun2TargetNextPos(AbstractTankBase base, Point2D.Double target) {
        double ga = base.getGunHeadingRadians();
        double ta = getPointAngle(base.getNextPosition(), target);

        return (abs(ta - ga) <= PI) ? ta - ga : ga - ta;
    }

    public static double AvoidNan(double value, double def) {
        return Double.isNaN(value) ? def : value;
    }

    public static List<Point> listClosePoint(Point pc, double d, int maxX, int maxY) {
        List<Point> closePoints = new ArrayList<>();
        int xX = (int) min(maxX, pc.x + d + 1);
        int xY = (int) min(maxY, pc.y + d + 1);
        for (int x = (int) max(0, pc.x - d - 1); x < xX; x++)
            for (int y = (int) max(0, pc.y - d - 1); y < xY; y++) {
                Point pt = new Point(x, y);
                if (pt.distance(pc) < d) {
                    closePoints.add(pt);
                }
            }
        return closePoints;
    }

    public static double normalDistrib(double x, double median, double deviation) {
        return (deviation > 0) ? 1 / (deviation * sqrt(2 * PI)) * exp(-.5 * pow((x - median) / deviation, 2)) : 1;
    }

    public static boolean collisionCircleSegment(Point2D.Double c, double r, Point2D.Double a, Point2D.Double b) {
        if (c.distance(a) < r || c.distance(b) < r)
            return true;

        double max_dist = max(c.distance(a), c.distance(b));
        double min_dist = (dot(vector(c, a), vector(b, a)) > 0 && dot(vector(c, b), vector(a, b)) > 0)
                ? triangleArea(c, a, b) * 2 / a.distance(b)
                : min(c.distance(a), c.distance(b));

        return min_dist <= r && max_dist >= r;
    }

    public static Point2D.Double vector(Point2D.Double a, Point2D.Double b) {
        return new Point2D.Double(b.getX() - a.getX(), b.getY() - a.getY());
    }

    public static double dot(Point2D.Double a, Point2D.Double b) {
        return a.getX() * b.getX() + a.getY() * b.getY();
    }

    public static double triangleArea(Point2D.Double a, Point2D.Double b, Point2D.Double c) {
        return Math.abs(a.getX() * (b.getY() - c.getY()) +
                b.getX() * (c.getY() - a.getY()) +
                c.getX() * (a.getY() - b.getY())) / 2.0;
    }

    public static Point2D.Double wallIntersection(Point2D.Double source, double direction) {
        return wallIntersection(source, direction, 0);
    }

    public static Point2D.Double wallIntersection(Point2D.Double source, double direction, double offset) {
        double cosD = cos(direction);
        double sinD = sin(direction);

        double minX = offset;
        double maxX = FIELD_WIDTH - offset;
        double minY = offset;
        double maxY = FIELD_HEIGHT - offset;

        double t = Double.POSITIVE_INFINITY;

        if (cosD > 0) {
            t = min(t, (maxX - source.x) / cosD);
        } else if (cosD < 0) {
            t = min(t, (minX - source.x) / cosD);
        }

        if (sinD > 0) {
            t = min(t, (maxY - source.y) / sinD);
        } else if (sinD < 0) {
            t = min(t, (minY - source.y) / sinD);
        }

        return new Point2D.Double(source.x + cosD * t, source.y + sinD * t);
    }

    public static double wallDistance(Point2D.Double p) {
        return min(min(p.x, FIELD_WIDTH - p.x), min(p.y, FIELD_HEIGHT - p.y));
    }

    @SafeVarargs
    public static <T> T[] concatArray(T[] first, T[]... rest) {
        if (rest == null)
            return Arrays.copyOf(first, first.length);

        int totalLength = first.length;
        for (T[] array : rest) totalLength += array.length;
        T[] result = Arrays.copyOf(first, totalLength);
        int offset = first.length;

        for (T[] array : rest) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }

    public static double[] concatArray(double[] first, double[]... rest) {
        int totalLength = first.length;
        for (double[] array : rest) totalLength += array.length;
        double[] result = Arrays.copyOf(first, totalLength);
        int offset = first.length;
        for (double[] array : rest) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }

    public static boolean pointInBattleField(Point2D.Double p, double offset) {
        return p.x >= offset && p.x < FIELD_WIDTH - offset && p.y >= offset && p.y < FIELD_HEIGHT - offset;
    }

    public static boolean pointInBattleField(Point2D.Double p) {
        return pointInBattleField(p, 0);
    }

    public static double directToWallDistance(Point2D.Double target, double heading) {
        Point2D.Double d = wallIntersection(target, heading, TANK_SIZE / 2);
        return d.distance(target);
    }

    public static Point2D.Double movePoint(Point2D.Double pt, double direction, double distance) {
        double newX = pt.x + distance * cos(direction);
        double newY = pt.y + distance * sin(direction);
        return new Point2D.Double(newX, newY);
    }

    public static double minMax(double value, double min, double max) {
        return max(min, min(max, value));
    }
}
