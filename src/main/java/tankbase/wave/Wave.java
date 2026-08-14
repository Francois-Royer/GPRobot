package tankbase.wave;

import tankbase.AbstractTankBase;
import tankbase.FieldMap;
import tankbase.ITank;
import tankbase.MovingPoint;
import tankbase.TankState;

import java.awt.geom.Point2D;

import static java.lang.Math.PI;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.toDegrees;
import static robocode.Rules.MAX_BULLET_POWER;
import static robocode.Rules.getBulletDamage;
import static robocode.Rules.getBulletSpeed;
import static robocode.util.Utils.normalAbsoluteAngle;
import static tankbase.AbstractTankBase.DISTANCE_MAX;
import static tankbase.AbstractTankBase.sysout;
import static tankbase.AbstractTankDrawingBase.INFO_LEVEL;
import static tankbase.Constant.TANK_SIZE;
import static tankbase.TankUtils.collisionCircleSegment;
import static tankbase.TankUtils.getPointAngle;
import static tankbase.TankUtils.getVertexAngle;
import static tankbase.TankUtils.middle;
import static tankbase.TankUtils.normalDistrib;
import static tankbase.enemy.EnemyDB.filterEnemies;

public class Wave extends MovingPoint {
    /*
        Waves are detected bullets fired by enemy, bullet position is on an arc like wave
     */

    private transient ITank source;
    private transient ITank target;

    private transient AbstractTankBase robotBase;

    private Point2D.Double waveStart;
    private TankState head;
    private Point2D.Double middle;
    private TankState circular;

    private double arc;
    private double median;
    private double normalMedian;
    private double deviation;

    public Wave(ITank target, double power, long start, ITank source) {
        super(source.getState(), getBulletSpeed(power), 0, start);
    }

    public Wave(ITank target, double power, long start, ITank source, double ratio) {
        super(source.getState(), getBulletSpeed(power), 0, start);
        if (INFO_LEVEL > 1)
            sysout.printf("Wave detected from %s x=%.0f y=%.0f%n", source.getName(), x, y);

        waveStart = source.getState();
        this.source = source;
        this.target = target;
        head = circular = target.getState();
        double time = (head.distance(waveStart) / getVelocity());
        while (time-- > 0)
            circular = circular.extrapolateNextState(false);
        arc = min(max(getVertexAngle(this, circular, head), PI / 8), PI / 4);
        deviation = arc / 3;

        setRatio(ratio);
    }

    public double getPower() {
        return (getVelocity() - 20.0D) / -3.0D;
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

    public Double getWaveStart() {
        return waveStart;
    }

    public Double getHead() {
        return head;
    }

    public Double getCircular() {
        return circular;
    }

    public Double getMiddle() {
        return middle;
    }

    public void setRatio(double ratio) {
        middle = middle(head, circular, ratio);
        setDirection(getPointAngle(waveStart, middle));
        median = normalAbsoluteAngle(getDirection());
        normalMedian = normalDistrib(median, median, deviation);
    }

    public Wave updateWithRatio(double ratio) {
        Wave w = new Wave(target, getPower(), getStart(), source);
        w.waveStart = waveStart;
        w.source = source;
        w.head = head;
        w.circular = circular;
        w.arc = arc;
        w.deviation = deviation;
        w.setRatio(ratio);

        return w;
    }

    @Override
    public String toString() {
        return String.format("Wave{target=%s, source=%s, p=%.1f, d=%.0f°, a=%.1f}", target.getName(), source.getName(), getPower(),
                toDegrees(getDirection()), toDegrees(arc));
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


