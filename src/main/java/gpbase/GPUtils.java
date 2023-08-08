package gpbase;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static gpbase.GPBase.*;
import static java.lang.Math.*;
import static robocode.Rules.*;
import static robocode.util.Utils.normalAbsoluteAngle;
import static robocode.util.Utils.normalRelativeAngle;

public class GPUtils {

    // -PI -> PI
    public static double getAngle(Point.Double a, Point.Double b) {
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
        return acos( (pow(sa, 2) + pow(sb, 2) - pow(ab, 2))/2/sa/sb);
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
        if (minv==maxv) return minr;
        return (maxr> minr)
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
        int s=d-5;
        int e=d+5;
        g2D.drawArc((int) w.x - d, (int) w.y - d, 2 * d, 2 * d, a - waveArc/2, waveArc);
        g2D.drawLine((int)(w.x + s*cos(w.direction)), (int)(w.y + s*sin(w.direction)),
                (int)(w.x + e*cos(w.direction)), (int)(w.y + e*sin(w.direction)));
        g2D.drawLine((int)(w.x + s*cos(w.direction+w.arc/2)), (int)(w.y + s*sin(w.direction+w.arc/2)),
                (int)(w.x + e*cos(w.direction+w.arc/2)), (int)(w.y + e*sin(w.direction+w.arc/2)));
        g2D.drawLine((int)(w.x + s*cos(w.direction-w.arc/2)), (int)(w.y + s*sin(w.direction-w.arc/2)),
                (int)(w.x + e*cos(w.direction-w.arc/2)), (int)(w.y + e*sin(w.direction-w.arc/2)));
    }

    public static int degree(double radians) {
        return (int) (normalAbsoluteAngle(radians) * 180 / PI);
    }

    static public Point.Double clonePoint(Point.Double p) {
        return new Point.Double(p.getX(), p.getY());
    }

    static public double maximumEscapeAngle(double vb) {
        return asin(MAX_VELOCITY/vb);
    }

    static public Point.Double midle(Point.Double a, Point.Double b) {
        return new Point.Double((a.getX()+b.getX())/2,(a.getY()+b.getY())/2);
    }

    static double computeTurnGun2Target(GPBase base, Point.Double target) {
        double ga = trigoAngle(base.getGunHeadingRadians());
        double ta = getAngle(base.getCurrentPoint(), target);
        double angle =  (abs(ta - ga) <= PI) ? ta - ga : ga - ta;

        if (abs(angle) < GUN_TURN_RATE_RADIANS)
            return angle;

        return computeTurnGun2TargetNextPos(base, target);
    }

    static double computeTurnGun2TargetNextPos(GPBase base, Point.Double target) {
        double ga = trigoAngle(base.getGunHeadingRadians());
        double ta = getAngle(base.getNextPoint(), target);

        return  (abs(ta - ga) <= PI) ? ta - ga : ga - ta;
    }

    static public double AvoidNan(double value, double def) {
        return Double.isNaN(value) ? def : value;
    }

    static Point getMinPoint(double a[][]) {
        double min = Double.MAX_VALUE;
        Point mp = new Point(0,0);
        for (int x=0; x<a.length; x++)
            for (int y=0; y<a[x].length; y++) {
                if (a[x][y] < min) {
                    min = a[x][y];
                    mp = new Point(x, y);
                }
            }
        return mp;
    }
    static Point getMaxPoint(double a[][]) {
        double max = 0;
        Point mp = new Point(0,0);
        for (int x=0; x<a.length; x++)
            for (int y=0; y<a[x].length; y++) {
                if (a[x][y] > max) {
                    max = a[x][y];
                    mp = new Point(x, y);
                }
            }
        return mp;
    }

    static Point getMinPointClose(double a[][], int h, int v, int d) {
        double min = Double.MAX_VALUE;
        Point pc = new Point(h, v);
        Point mp = new Point(0,0);
        for (int x=0; x<a.length; x++)
            for (int y=0; y<a[x].length; y++) {
                Point pt = new Point(x, y);
                if (a[x][y] < min && pt.distance(pc)<d) {
                    min = a[x][y];
                    mp = new Point(x, y);
                }
            }
        return mp;
    }

    static List<Point> listClosePoint(Point pc, double d, int maxX, int maxY) {
        List<Point> closePoints = new ArrayList<>();
        maxX = (int) min(maxX, pc.x+d+1);
        maxY = (int) min(maxY, pc.y+d+1);
        for (int x=(int) max(0, pc.x-d-1); x<maxX; x++)
            for (int y=(int) max(0, pc.y-d-1); y<maxY; y++) {
                Point pt = new Point(x, y);
                if (pt.distance(pc)<d) {
                    closePoints.add(pt);
                }
            }
        return closePoints;
    }
    static List<Point> listCirclePoint(Point pc, double d, int maxX, int maxY) {
        List<Point> points = new ArrayList<>();
        int num = (int) (d * PI * 2.4);
        Point p = new Point(-1,-1);
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

    static double computeMoveDanger(Point from, Point to, double dangerMap[][]) {
        int d = (int)Math.ceil(from.distance(to));
        if (d == 0) return Double.MAX_VALUE;
        double danger=0;
        for (int p=0; p<=d; p++) {
            int x=from.x + p*(to.x-from.x)/d;
            int y=from.y + p*(to.y-from.y)/d;
            if (x<dangerMap.length && y <dangerMap[x].length)
                danger += dangerMap[x][y];
        }
        return danger/pow(d,2);
    }

    static double normalDistrib(double x, double median, double deviation) {
        return (deviation>0) ? 1/(deviation*sqrt(2*PI))*exp(-.5*pow((x-median)/deviation, 2)) : 1;
    }


    static boolean collisionDroiteSeg(Point.Double A,Point.Double B,Point.Double O,Point.Double P)
    {
        double abx  = B.x - A.x;
        double aby = B.y - A.y;
        double apx = P.x - A.x;
        double apy = P.y - A.y;
        double aox = O.x - A.x;
        double aoy = O.y - A.y;
        if ((abx*apy - aby*apx)*(abx*aoy - aby*aox)<0)
            return true;
        else
            return false;
    }

    static boolean collisionSegSeg(Point.Double A,Point.Double B,Point.Double O,Point.Double P)
    {
        if (collisionDroiteSeg(A,B,O,P)==false)
            return false;
        if (collisionDroiteSeg(O,P,A,B)==false)
            return false;
        return true;
    }

    static boolean collisionCercleSeg(Point.Double c, double r,Point.Double O,Point.Double P)
    {
        if (c.distance(O) < r || c.distance(P) < r)
            return true;

        double d = O.distance(P);
        double angle= acos((O.x-P.x)/d)+ PI/2;

        Point.Double a = new Point.Double(c.x +r*cos(angle), c.y+ r*sin(angle));
        Point.Double b = new Point.Double(c.x +r*cos(angle+PI), c.y+ r*sin(angle+PI));
        Point.Double e = new Point.Double(c.x +r*cos(-angle), c.y+ r*sin(-angle));
        Point.Double f = new Point.Double(c.x +r*cos(-angle+PI), c.y+ r*sin(-angle+PI));

        return collisionSegSeg(a, b, O, P) ||
                collisionSegSeg(e, f, O, P);
    }

    static Point.Double wallIntersection(Point.Double source, double direction) {
        if (direction == PI/2) return new Point.Double(source.getX(), FIELD_HEIGHT);
        if (direction == -PI/2) return new Point.Double(source.getX(), 0);
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
            double x = source.getX() + (FIELD_HEIGHT -source.getY()) / tan(direction);
            return new Point.Double(x,  FIELD_HEIGHT);
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
        return new Point.Double(x,  0);
    }

    static <T> T[] concatArray(T[] first, T[] ... rest){
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
    static double[] concatArray(double[] first, double[] ... rest){
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
}
