package gpbase;

import gpbase.kdtree.KdTree;

import static gpbase.GPBase.*;
import static java.lang.Math.*;
import static robocode.Rules.*;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.List;

import static gpbase.GPUtils.*;
import static robocode.util.Utils.normalAbsoluteAngle;

public class Wave extends MovingPoint {
    Enemy enemy;
    double arc;
    Point.Double head;
    Point.Double middle;
    Point.Double circular;

    double median;
    double normalMedian;
    double deviation;
    double[] fireKdPoint;

    boolean kdangle = false;

    public Wave(Enemy enemy, double power, long start, Point.Double origin, GPBase gpbase, double[] fireKdPoint) {
        super(origin, getBulletSpeed(power), 0, start);
        this.enemy = enemy;
        this.fireKdPoint = fireKdPoint;
        head = gpbase.getCurrentPoint();
        double distance = origin.distance(head);
        double time = distance / velocity;
        double rv = gpbase.getVelocity();

        circular = new Point.Double();
        circular.x = head.x + cos(trigoAngle(gpbase.getHeadingRadians())) * time * rv;
        circular.y = head.y + sin(trigoAngle(gpbase.getHeadingRadians())) * time * rv;

        this.arc = max(getVertexAngle(origin, circular, head), .17);
        middle = midle(head, circular);

        direction = getAngle(origin, middle);

        if (enemy.getFireKdTree() != null) {
            List<KdTree.Entry<java.lang.Double>> el = enemy.getFireKdTree().nearestNeighbor(enemy.getFireKdPoint(power), 1, true);

            if (el.size() == 1 && el.get(0).distance < 10) {
                direction = trigoAngle(el.get(0).value);
                kdangle = true;
            }
        }

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
        if (d >= MAX_DANGER_RADIUS|| true) {
            danger *= normalDistrib(angle + median, median, deviation) / normalMedian;
            danger *= Math.pow((DANGER_DISTANCE_MAX - d) / DANGER_DISTANCE_MAX, 1);
        }
        return danger;
    }
}
