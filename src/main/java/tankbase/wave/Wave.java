package tankbase.wave;

import tankbase.*;
import tankbase.gun.Aiming;

import java.awt.*;
import java.awt.geom.Point2D;

import static java.lang.Math.*;
import static robocode.Rules.*;
import static robocode.util.Utils.normalAbsoluteAngle;
import static tankbase.AbstractTankBase.DISTANCE_MAX;
import static tankbase.AbstractTankBase.sysout;
import static tankbase.AbstractTankDrawingBase.INFO_LEVEL;
import static tankbase.Constant.TANK_SIZE;
import static tankbase.TankUtils.*;
import static tankbase.enemy.EnemyDB.filterEnemies;

public class Wave extends MovingPoint {

    /*
        Waves are detected bullets fired by enemy, bullet position is on an arc like wave
     */

    private transient ITank source;
    private transient ITank target;

    private transient AbstractTankBase robotBase;

    private double arc;

    private Point2D.Double head;
    private Point2D.Double middle;
    private Point2D.Double circular;

    private double median;
    private double normalMedian;
    private double deviation;

    public Wave(Aiming ad, long start) {
        this(ad.getTarget(), ad.getFirePower(), start, ad.getGun().getFirer());
    }

    public Wave(ITank target, double power, long start, ITank source, int headCount, int circularCount) {
        this(target, power, start, source);
        middle = middle(head, circular, headCount, circularCount);
        direction = getPointAngle(this, middle);
    }

    public Wave(ITank target, double power, long start, ITank source) {
        super(source.getState(), getBulletSpeed(power), 0, start);
        if (INFO_LEVEL > 1)
            sysout.printf("Wave detected from %s x=%.0f y=%.0f%n", source.getName(), x, y);

        this.source = source;
        this.target = target;

        head = target.getState();
        double distance = distance(head);
        double time = distance / velocity;

        circular = new Point.Double();
        circular.x = head.x + cos(target.getState().getHeadingRadians()) * time * target.getState().getVelocity();
        circular.y = head.y + sin(target.getState().getHeadingRadians()) * time * target.getState().getVelocity();

        this.arc = min(max(getVertexAngle(this, circular, head), PI / 8), PI / 3);
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
        double scale = FieldMap.getScale();
        Point2D.Double p = new Point2D.Double(x * scale + scale / 2, y * scale + scale / 2);
        double r = distance(p);

        if (d > r)
            return 0;

        boolean shadowed = filterEnemies(e -> e.isAlive() && e != source).stream()
                .map(e -> collisionCircleSegment(e.getState(), TANK_SIZE, p, waveNow))
                .reduce((a, b) -> a || b)
                .orElse(false);

        if (shadowed)
            return 0;

        double angle = getVertexAngle(this, waveNow, p);

        double danger = max(0.3, getBulletDamage(getPower()) / getBulletDamage(MAX_BULLET_POWER));
        d = p.distance(waveNow);
        if (d >= 0) {
            danger *= normalDistrib(angle + median, median, deviation) / normalMedian;
            danger *= Math.pow((DISTANCE_MAX - d) / DISTANCE_MAX, 2);
        }

        return danger;
    }

    public ITank getSource() {
        return source;
    }

    public double getArc() {
        return arc;
    }

    public Double getHead() {
        return head;
    }

    public Double getCircular() {
        return circular;
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


