package tankbase;

import tankbase.gun.AimingData;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.Optional;

import static java.lang.Math.PI;
import static java.lang.Math.cos;
import static java.lang.Math.max;
import static java.lang.Math.sin;
import static robocode.Rules.MAX_BULLET_POWER;
import static robocode.Rules.getBulletSpeed;
import static robocode.util.Utils.normalAbsoluteAngle;
import static tankbase.TankBase.*;
import static tankbase.TankUtils.*;

public class Wave extends MovingPoint {
    ITank source;
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

    public Wave(AimingData ad, long start) {
        this(ad.getTarget(), ad.getFirePower(), start, ad.getGunner().getTank());
    }

    public Wave(ITank target, double power, long start, ITank source, int headCount, int circularCount) {
        this(target, power, start, source);
        middle = middle(head, circular, headCount, circularCount);
        direction = getPointAngle(this, middle);
    }

    public Wave(ITank target, double power, long start, ITank source) {
        super(source.getPosition(), getBulletSpeed(power), 0, start);

        this.source = source;
        this.target = target;

        head = target.getPosition();
        double distance = distance(head);
        double time = distance / velocity;

        circular = new Point.Double();
        circular.x = head.x + cos(target.getHeadingRadians()) * time * target.getVelocity();
        circular.y = head.y + sin(target.getHeadingRadians()) * time * target.getVelocity();

        this.arc = max(getVertexAngle(this, circular, head), PI/8);
        middle = TankUtils.middle(head, circular);
        direction = getPointAngle(this, middle);
        median = normalAbsoluteAngle(direction);
        deviation = arc / 3;
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

        boolean shadowed = getEnemys()
                                   .filter(Enemy::isAlive)
                                   .map(e -> collisionCercleSeg(e.getPosition(), TANK_SIZE, p, this))
                                   .reduce((a, b) -> a||b)
                                   .orElse(false);

        if (shadowed)
            return 0;

        double angle = getVertexAngle(this, waveNow, p);

        d = p.distance(waveNow) / DANGER_SCALE;
        double danger = getPower() / MAX_BULLET_POWER;
        if (d >= 0) { //MAX_DANGER_RADIUS) {
            danger *= normalDistrib(angle + median, median, deviation) / normalMedian;
            danger *= Math.pow((DANGER_DISTANCE_MAX - d) / DANGER_DISTANCE_MAX, .5);
        }


        //if (angle > arc + deviation || angle < arc - deviation)
            //danger = 0;



        return danger;
    }

    public ITank getSource() {
        return source;
    }
}


