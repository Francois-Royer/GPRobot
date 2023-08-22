package tankbase.gun;

import tankbase.ITank;
import tankbase.Move;
import tankbase.TankUtils;
import tankbase.kdtree.KdTree;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.max;
import static robocode.Rules.*;
import static robocode.util.Utils.normalRelativeAngle;
import static tankbase.Enemy.MAX_GUN_HEAT;
import static tankbase.TankBase.*;
import static tankbase.TankUtils.clonePoint;
import static java.lang.Math.*;
import static tankbase.TankUtils.concatArray;

abstract public class AbstractKdTreeGunner extends AbtractGunner {

    public AbstractKdTreeGunner(ITank tank) {
        super(tank);
    }


    public AimingData getKdTreeAimingData(ITank target, List<KdTree.Entry<List<Move>>> el) {
        double firePower = getFirePower(target);
        Point.Double firingPosition = null;
        List<Point.Double> expectedMoves;
        if (el.size() == 0) return null;
        while (firePower >= MIN_BULLET_POWER && firingPosition == null) {
            for (KdTree.Entry<List<Move>> kdEntry: el) {
                expectedMoves = new ArrayList<>();
                List<Move> movesLog = kdEntry.value;
                firingPosition = getFiringPosition(target, firePower, movesLog, expectedMoves);
                if (firingPosition != null)
                    return new AimingData(this, target, firingPosition, firePower, expectedMoves);
            }
            firePower -=.1;
        }

        return null;
    }

    @Override
    public Color getColor() { return Color.RED; }

    private Point.Double getFiringPosition(ITank target, double firePower, List<Move> movesLog, List<Point.Double> predMoves) {
        Point.Double from = getTank().getPosition();
        double bulletSpeed = getBulletSpeed(firePower);
        Point.Double firePoint = clonePoint(target.getPosition());
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

            while (moveDuration < time && step < movesLog.size()) {
                Move m = movesLog.get(step++);
                long overtime = moveDuration - time;
                double dist = m.getVelocity() * m.getDuration();
                dir += m.getTurn();

                if (overtime > 0) {
                    dist -= m.getVelocity() * overtime;
                    dir -= m.getTurn() * overtime / m.getDuration();
                }

                firePoint.x += dist * cos(dir);
                firePoint.y += dist * sin(dir);

                if (! TankUtils.pointInBattleField(firePoint, (double) TANK_SIZE / 2.5))
                    return null;


                predMoves.add(clonePoint(firePoint));
                moveDuration += m.getDuration();
            }
        }

        return firePoint;
    }
    static public double[] patternWeights = {1,1,1,1};

    static public double[] getPatternPoint(ITank target) {
        Point.Double pos = target.getPosition();
        return new double[] {
                target.getPosition().distance(TankUtils.wallIntersection(target.getPosition(),
                        target.getMovingDirection()))/max(FIELD_WIDTH,FIELD_HEIGHT),
                target.getVelocity() / MAX_VELOCITY,
                target.getAccel() / (target.isDecelerate() ? DECELERATION : ACCELERATION),
                target.getTurnRate() / MAX_TURN_RATE_RADIANS,
        };
    }

    static public double[] surferWeights = {1,1,1,1,1,1,1, 1};
    static public double[] getSurferPoint(ITank target, ITank source) {
        List<AimingData> aimLog = source.getAimingLog(target.getName());


        return concatArray(getPatternPoint(target),
                new double[] {
                        target.getPosition().distance(source.getPosition())/DISTANCE_MAX,
                        normalRelativeAngle(target.getHeadingRadians() -
                                TankUtils.getPointAngle(source.getPosition(), target.getPosition()))/PI,
                });
    }

}
