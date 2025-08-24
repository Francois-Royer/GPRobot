package tankbase;

import tankbase.gun.Aiming;

import java.awt.*;
import java.awt.geom.Point2D;

import static java.lang.Math.PI;
import static java.lang.Math.cos;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.sin;
import static java.lang.Math.toDegrees;
import static robocode.Rules.MAX_BULLET_POWER;
import static robocode.Rules.getBulletDamage;
import static robocode.Rules.getBulletSpeed;
import static robocode.util.Utils.normalAbsoluteAngle;
import static tankbase.AbstractTankBase.DISTANCE_MAX;
import static tankbase.AbstractTankBase.getEnemys;
import static tankbase.Constant.TANK_SIZE;
import static tankbase.TankUtils.collisionCircleSegment;
import static tankbase.TankUtils.getPointAngle;
import static tankbase.TankUtils.getVertexAngle;
import static tankbase.TankUtils.middle;
import static tankbase.TankUtils.normalDistrib;

public class Wave extends MovingPoint {
    transient ITank source;
    transient ITank target;

    transient AbstractTankBase robotBase;

    double arc;
    Point2D.Double head;
    Point2D.Double middle;
    Point2D.Double circular;

    double median;
    double normalMedian;
    double deviation;

    public Wave(Aiming ad, long start) {
        this(ad.getTarget(), ad.getFirePower(), start, ad.getGunner().getGunner());
    }

    public Wave(ITank target, double power, long start, ITank source, int headCount, int circularCount) {
        this(target, power, start, source);
        middle = middle(head, circular, headCount, circularCount);
        direction = getPointAngle(this, middle);
    }

    public Wave(ITank target, double power, long start, ITank source) {
        super(source.getState().getPosition(), getBulletSpeed(power), 0, start);

        this.source = source;
        this.target = target;

        head = target.getState().getPosition();
        double distance = distance(head);
        double time = distance / velocity;

        circular = new Point.Double();
        circular.x = head.x + cos(target.getState().getHeadingRadians()) * time * target.getState().getVelocity();
        circular.y = head.y + sin(target.getState().getHeadingRadians()) * time * target.getState().getVelocity();

        this.arc = min(max(getVertexAngle(this, circular, head), PI / 12), PI / 4);
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
        Point2D.Double p = new Point2D.Double(x * FieldMap.getScale(), y * FieldMap.getScale());
        double r = distance(p);

        if (d > r)
            return 0;

        boolean shadowed = getEnemys()
                .filter(Enemy::isAlive)
                .filter(e -> e != source)
                .map(e -> collisionCircleSegment(e.getState().getPosition(), TANK_SIZE, p, waveNow))
                .reduce((a, b) -> a || b)
                .orElse(false);

        if (shadowed)
            return 0;

        double angle = getVertexAngle(this, waveNow, p);

        double danger = getBulletDamage(getPower()) / getBulletDamage(MAX_BULLET_POWER);
        d = p.distance(waveNow);
        if (d >= 0) {
            danger *= normalDistrib(angle + median, median, deviation) / normalMedian;
            danger *= Math.pow((DISTANCE_MAX - d) / DISTANCE_MAX, .5);
        }

        return danger;
    }

    public ITank getSource() {
        return source;
    }

    @Override
    public String toString() {
        return String.format("Wave{target=%s, source=%s, p=%.1f, d=%.0f°, a=%.1f}", target.getName(), source.getName(), getPower(),
                             toDegrees(direction), toDegrees(arc));
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}


