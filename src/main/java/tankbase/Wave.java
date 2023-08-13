package tankbase;

import tankbase.kdtree.KdTree;

import static tankbase.TankBase.*;
import static java.lang.Math.*;
import static robocode.Rules.*;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.List;

import static tankbase.TankUtils.*;
import static robocode.util.Utils.normalAbsoluteAngle;

public class Wave extends MovingPoint {
    ITank target;

    TankBase robotBase;

    double arc;
    Point.Double head;
    Point.Double middle;
    Point.Double circular;

    double median;
    double normalMedian;
    double deviation;
    boolean kdangle = false;

    public Wave(ITank target, double power, long start, Point.Double origin, ITank source) {
        super(origin, getBulletSpeed(power), 0, start);
        this.target = target;
        head = source.getPosition();
        double distance = origin.distance(head);
        double time = distance / velocity;
        double rv = source.getVelocity();

        circular = new Point.Double();
        circular.x = head.x + cos(source.getHeadingRadians()) * time * rv;
        circular.y = head.y + sin(source.getHeadingRadians()) * time * rv;

        this.arc = max(getVertexAngle(origin, circular, head), .3);
        middle = midle(head, circular);

        direction = getPointAngle(origin, middle);

        median = normalAbsoluteAngle(direction);
        deviation = arc / 4;
        normalMedian = normalDistrib(median, median, deviation);
    }

    public double getPower() {
        return (velocity - 20.0D) / -3.0D;
    }

    public double getDanger(int x, int y, long now) {
        Point2D.Double waveNow = getPosition(now);
        double d = getDistance(now);
        Point2D.Double p = new Point2D.Double(x * DANGER_SCALE, y * DANGER_SCALE);
        double r = distance(p);

        if (d > r)
            return 0;

        double angle = getVertexAngle(this, waveNow, p);

        d = p.distance(waveNow) / DANGER_SCALE;
        double danger = getPower() / MAX_BULLET_POWER;
        if (d >= MAX_DANGER_RADIUS) {
            danger *= normalDistrib(angle + median, median, deviation) / normalMedian;
            danger *= Math.pow((DANGER_DISTANCE_MAX - d) / DANGER_DISTANCE_MAX, 1);
        }
        return danger;
    }
}
