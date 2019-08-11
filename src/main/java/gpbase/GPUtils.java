package gpbase;

import java.awt.*;

import static java.lang.Math.*;
import static robocode.util.Utils.*;
import static robocode.Rules.*;

public class GPUtils {
    // -PI -> PI
    public static double getAngle(Point.Double s, Point.Double d) {
        double a = acos((d.x - s.x) / s.distance(d));
        if (d.y < s.y)
            a = 2 * PI - a;
        return normalRelativeAngle(a);
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
                ? (v - minv) / (maxv - minv) * (maxr - minr)
                : (maxv - v) / (maxv - minv) * (minr - maxr);
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

    public static void drawWave(Graphics2D g, Color c, Wave w, long tick) {
        g.setColor(c);
        int waveArc = (int) (w.arc * 180 / PI);
        int d = (int) w.getDistance(tick);
        int a = (450 - (int) (normalAbsoluteAngle(w.direction) * 180 / PI)) % 360;

        g.drawArc((int) w.x - d, (int) w.y - d, 2 * d, 2 * d, a - waveArc, 2 * waveArc);
        //g.drawLine((int)w.x, (int)w.y, (int)(w.x + 10000*cos(w.direction)), (int)(w.y + 10000*sin(w.direction)));
        //g.drawLine((int)w.x, (int)w.y, (int)(w.x + 10000*cos(w.direction+w.arc)), (int)(w.y + 10000*sin(w.direction+w.arc)));
        //g.drawLine((int)w.x, (int)w.y, (int)(w.x + 10000*cos(w.direction-w.arc)), (int)(w.y + 10000*sin(w.direction-w.arc)));
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

    static public double firePowerFromConfidence(double confidence)  {
        return range(confidence , 0, 1, MIN_BULLET_POWER, MAX_BULLET_POWER);
    }
}