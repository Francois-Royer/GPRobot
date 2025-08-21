package tankbase.gun;

import robocode.Rules;
import tankbase.ITank;
import tankbase.Move;
import tankbase.TankBase;
import tankbase.TankUtils;
import tankbase.kdtree.KdTree;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.*;
import static robocode.Rules.*;
import static robocode.util.Utils.normalRelativeAngle;
import static tankbase.Enemy.MAX_GUN_HEAT;
import static tankbase.TankBase.*;
import static tankbase.TankUtils.*;

abstract public class AbstractKdTreeGunner extends AbtractGunner {

    public AbstractKdTreeGunner(ITank tank) {
        super(tank);
    }


    public AimingData getKdTreeAimingData(ITank target, List<KdTree.Entry<List<Move>>> el) {
        double firePower = getFirePower(target);
        Point.Double[] firingPosition = null;
        List<Point.Double> expectedMoves;
        if (el.size() == 0) return null;
        while (firePower >= MIN_BULLET_POWER && firingPosition == null) {
            for (KdTree.Entry<List<Move>> kdEntry : el) {
                expectedMoves = new ArrayList<>();
                List<Move> movesLog = kdEntry.value;
                firingPosition = getFiringPosition(target, firePower, movesLog, expectedMoves);
                if (firingPosition != null)
                    return new AimingData(this, target, firingPosition[0], firingPosition[1], firePower, expectedMoves, kdEntry);
            }
            firePower -= .1;
        }

        return null;
    }

    @Override
    public Color getColor() {
        return Color.RED;
    }

    private Point.Double[] getFiringPosition(ITank target, double firePower, List<Move> movesLog, List<Point.Double> predMoves) {
        Point.Double from = getTank().getPosition();
        double bulletSpeed = getBulletSpeed(firePower);
        Point.Double firePoint = clonePoint(target.getPosition());
        Point.Double prevPoint = null;
        long prevTime = 0;
        long prevDelta = Long.MAX_VALUE;

        for (int i = 0; i < 10; i++) {
            long time = (long) (from.distance(firePoint) / bulletSpeed);

            if (prevTime == time || abs(time - prevTime) >= prevDelta)
                break;
            prevDelta = abs(time - prevTime);
            prevTime = time;

            firePoint = clonePoint(target.getPosition());
            predMoves.clear();
            long moveDuration = 0;
            int step = 1;
            double dir = target.getHeadingRadians();

            while (moveDuration < time + 1 && step < movesLog.size()) {
                Move m = movesLog.get(step++);
                long overtime = moveDuration - time;
                double dist = m.getVelocity() * m.getDuration();
                dir += m.getTurn();

                if (overtime > 0) {
                    dist -= m.getVelocity() * overtime;
                    dir -= m.getTurn() * overtime / m.getDuration();
                }

                if (moveDuration == time)
                    prevPoint = clonePoint(firePoint);

                firePoint.x += dist * cos(dir);
                firePoint.y += dist * sin(dir);

                if (!TankUtils.pointInBattleField(firePoint, TANK_SIZE / 2.5))
                    return null;


                predMoves.add(clonePoint(firePoint));
                moveDuration += m.getDuration();
            }
        }

        if (prevPoint == null)
            return null;

        return new Point.Double[]{prevPoint, firePoint};
    }

static public double[] patternWeights = {10, 10, 10, 20, 20, 30, 30};

    static public double[] getPatternPoint(ITank target) {
        return new double[] {
                target.getVelocity() / MAX_VELOCITY,
                target.getHeadingRadians()%(PI/2),
                target.getPosition().distance(wallIntersection(target.getPosition(), target.getMovingDirection())) / DISTANCE_MAX,
                target.getTurnRate() / MAX_TURN_RATE_RADIANS,
                target.getAccel() / DECELERATION,
                min(target.getDate()-target.getLastStop(), 100)/100,
                min(target.getDate()-target.getLastChangeDirection(), 100)/100
        };
    }

    static public double[] _surferWeights = {10, 20, 100, 100, 100, 50, 50, 30, 30, 10, 10};
    static public double[] surferWeights = concatArray(patternWeights, _surferWeights);

    static public double[] getSurferPoint(ITank target, ITank source) {
        double[] surferPoint = {
                target.getPosition().distance(source.getPosition()) / DISTANCE_MAX,
                normalRelativeAngle(target.getHeadingRadians() - getPointAngle(source.getPosition(), target.getPosition())) / PI,
                source.getGunHeat()/ MAX_GUN_HEAT,
                0, 0, 0, 0, 0, 0, 0, 0
        };

        List<Shell> aimLog = source.getFireLog(target.getName());
        for (int i = 0; i < aimLog.size() && i * 2 + 4 < surferPoint.length; i++) {
            surferPoint[i * 2 + 3] = aimLog.get(i).getAimingData().getFirePower() / MAX_BULLET_POWER;
            surferPoint[i * 2 + 4] = aimLog.get(i).age(source.getDate()) / DISTANCE_MAX * Rules.getBulletSpeed(aimLog.get(i).getAimingData().getFirePower());
        }

        return concatArray(getPatternPoint(target), surferPoint);
    }

}
