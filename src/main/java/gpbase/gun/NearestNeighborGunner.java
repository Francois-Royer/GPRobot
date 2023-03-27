package gpbase.gun;

import gpbase.Enemy;
import gpbase.Move;
import gpbase.kdtree.KdTree;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static gpbase.GPBase.TANK_SIZE;
import static gpbase.GPBase.pointInBattleField;
import static gpbase.GPUtils.clonePoint;
import static java.lang.Math.*;
import static java.lang.Math.abs;
import static robocode.Rules.MIN_BULLET_POWER;
import static robocode.Rules.getBulletSpeed;

public class NearestNeighborGunner extends AbtractGunner {

    @Override
    public AimingData aim(Enemy enemy) {
        if (enemy.getKdTree() == null) return null;

        double[] kdPoint = enemy.getKDPoint(enemy.getGpBase());
        double firePower = getFirePower(enemy)*2;
        //kdPoint[0] = 100;
        List<KdTree.Entry<List<Move>>> el = enemy.getKdTree().nearestNeighbor(kdPoint, 5, true);
        Point.Double firingPosition = null;
        List<Point.Double> expectedMoves;

        while (firePower > MIN_BULLET_POWER && firingPosition == null) {
            firePower/=2;
            for (KdTree.Entry<List<Move>> kdEntry:el) {
                expectedMoves = new ArrayList<>();
                List<Move> movesLog = kdEntry.value;
                firingPosition = getFiringPosition(enemy, firePower, movesLog, expectedMoves);
                if (firingPosition != null)
                    return new AimingData(this, enemy, firingPosition, firePower, expectedMoves);
            }
        }

        return null;
    }

    @Override
    public Color getColor() { return Color.RED; }

    private Point.Double getFiringPosition(Enemy target, double firePower, List<Move> movesLog, List<Point.Double> predMoves) {
        Point.Double from = target.getGpBase().getCurrentPoint();
        double bulletSpeed = getBulletSpeed(firePower);
        Point.Double firePoint = clonePoint(target);
        long prevTime=0;
        long prevDelta=Long.MAX_VALUE;

        for (int i = 0; i < 10; i++) {
            long time = (long) (from.distance(firePoint) / bulletSpeed);

            if (prevTime == time || abs(time-prevTime) >= prevDelta)
                break;
            prevDelta=abs(time-prevTime);
            prevTime = time;

            firePoint = clonePoint(target);
            predMoves.clear();
            long moveDuration = 0;
            int step = 1;
            double dir = target.getDirection();

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

                if (! pointInBattleField(firePoint,  (double) TANK_SIZE / 2.1))
                    return null;


                predMoves.add(clonePoint(firePoint));
                moveDuration += m.getDuration();
            }
        }

        return firePoint;
    }
}
