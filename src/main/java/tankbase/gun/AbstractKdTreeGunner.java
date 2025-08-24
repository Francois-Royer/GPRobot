package tankbase.gun;

import tankbase.ITank;
import tankbase.Move;
import tankbase.TankUtils;
import tankbase.kdtree.KdTree;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.abs;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static robocode.Rules.MIN_BULLET_POWER;
import static robocode.Rules.getBulletSpeed;
import static tankbase.AbstractTankBase.TANK_SIZE;
import static tankbase.TankUtils.clonePoint;

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
        Point.Double from = getGunner().getState().getPosition();
        double bulletSpeed = getBulletSpeed(firePower);
        Point.Double firePoint = clonePoint(target.getState().getPosition());
        Point.Double prevPoint = null;
        long prevTime = 0;
        long prevDelta = Long.MAX_VALUE;

        for (int i = 0; i < 10; i++) {
            long time = (long) (from.distance(firePoint) / bulletSpeed);

            if (prevTime == time || abs(time - prevTime) >= prevDelta)
                break;
            prevDelta = abs(time - prevTime);
            prevTime = time;

            firePoint = target.getState().getPosition();
            predMoves.clear();
            long moveDuration = 0;
            int step = 0;
            double dir = target.getState().getHeadingRadians();

            while (moveDuration < time + 1 && step < movesLog.size()) {
                Move m = movesLog.get(step++);
                long overtime = moveDuration - time;
                double dist = m.getDistance();
                dir += m.getTurn();

                if (overtime > 0) {
                    dist -= m.getDistance() * overtime;
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
}
