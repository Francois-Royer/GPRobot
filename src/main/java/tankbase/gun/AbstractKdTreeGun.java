package tankbase.gun;

import tankbase.ITank;
import tankbase.KDMove;
import tankbase.Move;
import tankbase.kdtree.KdTree;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.*;
import static robocode.Rules.MIN_BULLET_POWER;
import static robocode.Rules.getBulletSpeed;
import static tankbase.Constant.TANK_SIZE;
import static tankbase.TankUtils.clonePoint;
import static tankbase.TankUtils.pointInBattleField;

public abstract class AbstractKdTreeGun extends AbtractGun {

    protected AbstractKdTreeGun(ITank gunner) {
        super(gunner);
    }


    public Aiming getKdTreeAimingData(ITank target, List<KdTree.Entry<List<Move>>> el) {
        double firePower = getFirePower(target);
        Point2D.Double[] firingPosition = null;
        List<Point2D.Double> expectedMoves;
        if (el.isEmpty()) return null;
        while (firePower >= MIN_BULLET_POWER) {
            for (KdTree.Entry<List<Move>> kdEntry : el) {
                expectedMoves = new ArrayList<>();
                List<Move> movesLog = kdEntry.value;
                firingPosition = getFiringPosition(target, firePower, movesLog, expectedMoves);
                if (firingPosition != null)
                    return new Aiming(this, target, firingPosition[0], firingPosition[1], firePower, expectedMoves, kdEntry);
            }
            firePower -= .1;
        }

        return null;
    }

    private Point2D.Double[] getFiringPosition(ITank target, double firePower, List<Move> movesLog, List<Point2D.Double> predMoves) {
        Point2D.Double from = getFirer().getState();
        double bulletSpeed = getBulletSpeed(firePower);
        Point2D.Double firePoint = clonePoint(target.getState());
        Point2D.Double prevPoint = null;
        long prevTime = 0;
        long prevDelta = Long.MAX_VALUE;

        for (int i = 0; i < 10; i++) {
            long time = (long) (from.distance(firePoint) / bulletSpeed);

            if (prevTime == time || abs(time - prevTime) >= prevDelta)
                break;

            prevDelta = abs(time - prevTime);
            prevTime = time;

            firePoint = clonePoint(target.getState());
            predMoves.clear();
            long moveDuration = 0;
            int step = 0;
            double dir = target.getState().getHeadingRadians();

            while (moveDuration < time + 1 && step < movesLog.size()) {
                Move m = movesLog.get(step++);
                long overtime = moveDuration - time;
                double dist = m.distance();
                dir += m.turn();

                if (overtime > 0) {
                    dist -= m.distance() * overtime;
                    dir -= m.turn() * overtime / m.duration();
                }

                if (moveDuration == time)
                    prevPoint = clonePoint(firePoint);

                firePoint.x += dist * cos(dir);
                firePoint.y += dist * sin(dir);

                if (!pointInBattleField(firePoint, TANK_SIZE / 2.5))
                    return null;


                predMoves.add(clonePoint(firePoint));
                moveDuration += m.duration();
            }
        }

        if (prevPoint == null)
            return null;

        predMoves.remove(firePoint);
        predMoves.remove(prevPoint);
        return new Point2D.Double[]{prevPoint, firePoint};
    }
}
