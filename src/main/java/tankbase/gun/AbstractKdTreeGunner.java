package tankbase.gun;

import robocode.Rules;
import tankbase.ITank;
import tankbase.Move;
import tankbase.TankUtils;
import tankbase.kdtree.KdTree;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.lang.Math.max;
import static robocode.Rules.*;
import static robocode.util.Utils.normalRelativeAngle;
import static tankbase.TankBase.*;
import static java.lang.Math.*;
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
            for (KdTree.Entry<List<Move>> kdEntry: el) {
                expectedMoves = new ArrayList<>();
                List<Move> movesLog = kdEntry.value;
                firingPosition = getFiringPosition(target, firePower, movesLog, expectedMoves);
                if (firingPosition != null)
                    return new AimingData(this, target, firingPosition[0], firingPosition[1], firePower, expectedMoves);
            }
            firePower -=.1;
        }

        return null;
    }

    @Override
    public Color getColor() { return Color.RED; }

    private Point.Double[] getFiringPosition(ITank target, double firePower, List<Move> movesLog, List<Point.Double> predMoves) {
        Point.Double from = getTank().getPosition();
        double bulletSpeed = getBulletSpeed(firePower);
        Point.Double firePoint = clonePoint(target.getPosition());
        Point.Double prevPoint = null;
        long prevTime=0;
        long prevDelta=Long.MAX_VALUE;

        for (int i = 0; i < 10; i++) {
            long time = (long) (from.distance(firePoint) / bulletSpeed);

            if (prevTime == time || abs(time-prevTime) >= prevDelta)
                break;
            prevDelta=abs(time-prevTime);
            prevTime = time;

            firePoint = clonePoint(target.getPosition());
            predMoves.clear();
            long moveDuration = 0;
            int step = 1;
            double dir = target.getHeadingRadians();

            while (moveDuration < time+1 && step < movesLog.size()) {
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

                if (! TankUtils.pointInBattleField(firePoint, (double) TANK_SIZE / 2.5))
                    return null;


                predMoves.add(clonePoint(firePoint));
                moveDuration += m.getDuration();
            }
        }

        if (prevPoint == null)
            return null;

        return new Point.Double[]{prevPoint, firePoint};
    }

    static public double[] patternWeights = {10,10,10,10,10,10};

    static public double[] getPatternPoint(ITank target) {
        return new double[] {
                target.getVelocity() / MAX_VELOCITY,
                target.getAccel() / DECELERATION,
                target.getTurnRate() / MAX_TURN_RATE_RADIANS,
                target.getHeadingRadians() / PI,
                target.getPosition().distance(TankUtils.wallIntersection(target.getPosition(),
                        target.getMovingDirection()))/max(FIELD_WIDTH,FIELD_HEIGHT),
                wallDistance(target.getPosition())/min(FIELD_WIDTH/2,FIELD_HEIGHT/2)
        };
    }

    static public double[] _surferWeights = {10,10,10,10,5,5,5,3,3,3,1,1,1};
    static public double[] surferWeights = concatArray(patternWeights,_surferWeights);
    static public double[] getSurferPoint(ITank target, ITank source) {
        List<Shell> aimLog = source.getFireLog(target.getName());
        double[] surferPoint = { target.getPosition().distance(source.getPosition())/DISTANCE_MAX,
                normalRelativeAngle(target.getHeadingRadians() -
                TankUtils.getPointAngle(source.getPosition(), target.getPosition()))/PI,
                0, 0, 0, 0, 0, 0, 0, 0, 0};

        for (int i=0; i<aimLog.size() && i*3+4 < surferPoint.length ; i++) {
            surferPoint[i*3+2] = aimLog.get(i).getAimingData().getFirePower() / MAX_BULLET_POWER;
            surferPoint[i*3+3] = aimLog.get(i).age(source.getDate()) / DISTANCE_MAX * Rules.getBulletSpeed(aimLog.get(i).getAimingData().getFirePower());
            surferPoint[i*3+4] = source.getPosition().distance(target.getPosition());
        }

        return concatArray(getPatternPoint(target), surferPoint);
    }

}
