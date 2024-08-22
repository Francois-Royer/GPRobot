package tankbase;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.lang.Math.*;
import static robocode.Rules.MAX_VELOCITY;
import static robocode.util.Utils.normalAbsoluteAngle;
import static robocode.util.Utils.normalRelativeAngle;
import static tankbase.TankBase.FIELD_HEIGHT;
import static tankbase.TankBase.FIELD_WIDTH;

public class TankUtils {

    // -PI -> PI
    public static double getPointAngle(Point.Double a, Point.Double b) {
        double angle = acos((b.x - a.x) / a.distance(b));
        if (b.y < a.y)
            angle = 2 * PI - angle;
        return normalRelativeAngle(angle);
    }

    // Return angle of s vertex 0-> PI
    public static double getVertexAngle(Point.Double s, Point.Double a, Point.Double b) {
        double ab = a.distance(b);
        double sa = a.distance(s);
        double sb = b.distance(s);
        return acos((pow(sa, 2) + pow(sb, 2) - pow(ab, 2)) / 2 / sa / sb);
    }

    // -PI -> PI
    public static double oppositeAngle(double a) {
        return normalRelativeAngle(a + PI);
    }

    // convert robocode angle to trigonometric angle
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

    public static void drawFillCircle(Graphics2D g, Color c, Point.Double p, int d) {
        g.setColor(c);
        g.fillArc((int) p.x - d / 2, (int) p.y - d / 2, d, d, 0, 360);
    }

    public static void drawCircle(Graphics2D g, Color c, Point.Double p, int d) {
        g.setColor(c);
        g.drawArc((int) p.x - d / 2, (int) p.y - d / 2, d, d, 0, 360);
    }

    public static void drawAimCircle(Graphics2D g, Color c, Point.Double p, int d) {
        int div = 5;
        g.setColor(c);
        g.drawArc((int) p.x - d / 2, (int) p.y - d / 2, d, d, 0, 360);
        g.drawLine((int) p.x + d / 2, (int) p.y, (int) p.x - d / div, (int) p.y);
        g.drawLine((int) p.x - d / 2, (int) p.y, (int) p.x + d / div, (int) p.y);
        g.drawLine((int) p.x, (int) p.y - d / 2, (int) p.x, (int) p.y - d / div);
        g.drawLine((int) p.x, (int) p.y + d / 2, (int) p.x, (int) p.y + d / div);
    }

    public static void drawWave(Graphics2D g2D, Color c, Wave w, long tick) {
        g2D.setColor(c);
        int waveArc = (int) (w.arc * 180 / PI);
        int d = (int) w.getDistance(tick);
        int a = (450 - (int) (normalAbsoluteAngle(w.direction) * 180 / PI)) % 360;
        int s = d - 5;
        int e = d + 5;
        g2D.drawArc((int) w.x - d, (int) w.y - d, 2 * d, 2 * d, a - waveArc / 2, waveArc);
        g2D.drawLine((int) (w.x + s * cos(w.direction)), (int) (w.y + s * sin(w.direction)),
                (int) (w.x + e * cos(w.direction)), (int) (w.y + e * sin(w.direction)));
        g2D.drawLine((int) (w.x + s * cos(w.direction + w.arc / 2)), (int) (w.y + s * sin(w.direction + w.arc / 2)),
                (int) (w.x + e * cos(w.direction + w.arc / 2)), (int) (w.y + e * sin(w.direction + w.arc / 2)));
        g2D.drawLine((int) (w.x + s * cos(w.direction - w.arc / 2)), (int) (w.y + s * sin(w.direction - w.arc / 2)),
                (int) (w.x + e * cos(w.direction - w.arc / 2)), (int) (w.y + e * sin(w.direction - w.arc / 2)));
    }

    public static int degree(double radians) {
        return (int) (normalAbsoluteAngle(radians) * 180 / PI);
    }

    static public Point.Double clonePoint(Point.Double p) {
        return new Point.Double(p.getX(), p.getY());
    }

    static public double maximumEscapeAngle(double vb) {
        return asin(MAX_VELOCITY / vb);
    }

    static public Point.Double middle(Point.Double a, Point.Double b) {
        return middle(a, b, 1, 1);
    }

    static public Point.Double middle(Point.Double a, Point.Double b, int ac, int bc) {
        return new Point.Double((a.getX() * ac + b.getX() * bc) / (ac + bc), (a.getY() * ac + b.getY() * bc) / (ac + bc));
    }

    /*static double computeTurnGun2Target(TankBase base, Point.Double target) {
        double angle =  computeTurnGun2Target(base.getPosition(), target, base.getGunHeadingRadians());

        if (abs(angle) < GUN_TURN_RATE_RADIANS)
            return angle;

        return computeTurnGun2TargetNextPos(base, target);
    }*/

    static double computeTurnGun2Target(Point.Double base, Point.Double target, double ga) {
        double ta = getPointAngle(base, target);
        return (abs(ta - ga) <= PI) ? ta - ga : ga - ta;
    }

    static double computeTurnGun2TargetNextPos(TankBase base, Point.Double target) {
        double ga = base.getGunHeadingRadians();
        double ta = getPointAngle(base.getNextPosition(), target);

        return (abs(ta - ga) <= PI) ? ta - ga : ga - ta;
    }

    static public double AvoidNan(double value, double def) {
        return Double.isNaN(value) ? def : value;
    }

    static Point getMinPoint(double[][] a) {
        double min = Double.MAX_VALUE;
        Point mp = new Point(0, 0);
        for (int x = 0; x < a.length; x++)
            for (int y = 0; y < a[x].length; y++) {
                if (a[x][y] < min) {
                    min = a[x][y];
                    mp = new Point(x, y);
                }
            }
        return mp;
    }

    static Point getMaxPoint(double[][] a) {
        double max = 0;
        Point mp = new Point(0, 0);
        for (int x = 0; x < a.length; x++)
            for (int y = 0; y < a[x].length; y++) {
                if (a[x][y] > max) {
                    max = a[x][y];
                    mp = new Point(x, y);
                }
            }
        return mp;
    }

    static Point getMinPointClose(double[][] a, int h, int v, int d) {
        double min = Double.MAX_VALUE;
        Point pc = new Point(h, v);
        Point mp = new Point(0, 0);
        for (int x = 0; x < a.length; x++)
            for (int y = 0; y < a[x].length; y++) {
                Point pt = new Point(x, y);
                if (a[x][y] < min && pt.distance(pc) < d) {
                    min = a[x][y];
                    mp = new Point(x, y);
                }
            }
        return mp;
    }

    static List<Point> listClosePoint(Point pc, double d, int maxX, int maxY) {
        List<Point> closePoints = new ArrayList<>();
        maxX = (int) min(maxX, pc.x + d + 1);
        maxY = (int) min(maxY, pc.y + d + 1);
        for (int x = (int) max(0, pc.x - d - 1); x < maxX; x++)
            for (int y = (int) max(0, pc.y - d - 1); y < maxY; y++) {
                Point pt = new Point(x, y);
                if (pt.distance(pc) < d) {
                    closePoints.add(pt);
                }
            }
        return closePoints;
    }

    static List<Point> listCirclePoint(Point pc, double d, int maxX, int maxY) {
        List<Point> points = new ArrayList<>();
        int num = (int) (d * PI * 2.4);
        Point p = new Point(-1, -1);
        for (int i = 0; i < num; i++) {
            double a = i * 2 * PI / num;
            int x = (int) (pc.getX() + d * cos(a));
            int y = (int) (pc.getY() + d * sin(a));
            if (x < 0 || x >= maxX || y < 0 || y >= maxY || (x == p.getX() && y == p.getY()))
                continue;

            points.add(p = new Point(x, y));
        }
        return points;
    }

    static double computeMoveDanger(Point from, Point to, double[][] dangerMap) {
        int d = (int) Math.ceil(from.distance(to));
        if (d == 0) return Double.MAX_VALUE;
        double danger = 0;
        for (int p = 0; p <= d; p++) {
            int x = from.x + p * (to.x - from.x) / d;
            int y = from.y + p * (to.y - from.y) / d;
            if (x < dangerMap.length && y < dangerMap[x].length)
                danger += dangerMap[x][y];
        }
        return danger /= pow(d, 1.1);
    }

    static double normalDistrib(double x, double median, double deviation) {
        return (deviation > 0) ? 1 / (deviation * sqrt(2 * PI)) * exp(-.5 * pow((x - median) / deviation, 2)) : 1;
    }


    static boolean collisionCercleSeg(Point2D.Double c, double r, Point2D.Double a, Point2D.Double b) {
        if (c.distance(a) < r || c.distance(b) < r)
            return true;

        double max_dist = max(c.distance(a), c.distance(b));
        double min_dist = (dot(vector(c, a), vector(b, a)) > 0 && dot(vector(c, b), vector(a, b)) > 0)
                ? triangleArea(c, a, b) * 2 / a.distance(b)
                : min(c.distance(a), c.distance(b));
        double h = triangleArea(c, a, b) * 2 / a.distance(b);

        return min_dist <= r && max_dist >= r;
    }

    static Point2D.Double vector(Point2D.Double a, Point2D.Double b) {
        return new Point2D.Double(b.getX() - a.getX(), b.getY() - a.getY());
    }

    static double dot(Point2D.Double a, Point2D.Double b) {
        return a.getX() * b.getX() + a.getY() * b.getY();
    }

    static double triangleArea(Point2D.Double a, Point2D.Double b, Point2D.Double c) {
        return Math.abs(a.getX() * (b.getY() - c.getY()) +
                b.getX() * (c.getY() - a.getY()) +
                c.getX() * (a.getY() - b.getY())) / 2.0;
    }

    public static Point.Double wallIntersection(Point.Double source, double direction) {
        if (direction == PI / 2) return new Point.Double(source.getX(), FIELD_HEIGHT);
        if (direction == -PI / 2) return new Point.Double(source.getX(), 0);
        if (direction == 0) return new Point.Double(FIELD_WIDTH, source.getY());
        if (direction == PI) return new Point.Double(0, source.getY());

        if (direction > 0) {
            if (direction < PI / 2) {
                double y = source.getY() + (FIELD_WIDTH - source.getX()) * tan(direction);
                if (y <= FIELD_HEIGHT) return new Point.Double(FIELD_WIDTH, y);
                double x = source.getX() + (FIELD_HEIGHT - source.getY()) / tan(direction);
                return new Point.Double(x, FIELD_HEIGHT);
            }
            double y = source.getY() - source.getX() * tan(direction);
            if (y <= FIELD_HEIGHT) return new Point.Double(0, y);
            double x = source.getX() + (FIELD_HEIGHT - source.getY()) / tan(direction);
            return new Point.Double(x, FIELD_HEIGHT);
        }

        if (direction > -PI / 2) {
            double y = source.getY() + (FIELD_WIDTH - source.getX()) * tan(direction);
            if (y >= 0) return new Point.Double(FIELD_WIDTH, y);
            double x = source.getX() - (source.getY()) / tan(direction);
            return new Point.Double(x, 0);
        }

        double y = source.getY() - source.getX() * tan(direction);
        if (y >= 0) return new Point.Double(0, y);
        double x = source.getX() - source.getY() / tan(direction);
        return new Point.Double(x, 0);
    }

    public static <T> T[] concatArray(T[] first, T[]... rest) {
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

    public static boolean pointInBattleField(Point.Double p, double offset) {
        return p.x >= offset && p.x < FIELD_WIDTH - offset && p.y >= offset && p.y < FIELD_HEIGHT - offset;
    }

    static public boolean pointInBattleField(Point2D.Double p) {
        return pointInBattleField(p, 0);
    }

    static public Double wallDistance(Point2D.Double p) {
        return min(min(p.x, FIELD_WIDTH - p.x), min(p.y, FIELD_HEIGHT - p.y));
    }
}
