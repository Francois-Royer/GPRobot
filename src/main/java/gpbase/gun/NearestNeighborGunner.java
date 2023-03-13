package gpbase.gun;

import gpbase.Enemy;
import gpbase.Move;
import gpbase.kdtree.KdTree;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static gpbase.GPUtils.clonePoint;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static robocode.Rules.getBulletSpeed;

public class NearestNeighborGunner extends AbtractGunner {
    public NearestNeighborGunner() {
    }

    @Override
    public AimingData aim(Enemy enemy) {
        if (enemy.getKdTree() == null) return null;

        double[] kdPoint = enemy.getKDPoint(enemy.getGpBase());
        kdPoint[0] = 100;
        List<KdTree.Entry<List<Move>>> el = enemy.getKdTree().nearestNeighbor(kdPoint, 5, true);

        /*System.out.printf("==========================================================\n");
        for (KdTree.Entry<List<Move>> kdEntry:el)
            System.out.printf("Kdistance=%f\n", kdEntry.distance);*/

        for (KdTree.Entry<List<Move>> kdEntry:el) {
            //System.out.printf(">>>>> Kdistance=%f\n", kdEntry.distance);
            List<Move> movesLog = kdEntry.value;
            double firePower = getFirePower(enemy);
            List<Point.Double> expectedMoves = new ArrayList<>();
            Point.Double firingPosition = getFiringPosition(enemy, firePower, movesLog, expectedMoves);
            if (firingPosition == null) {
                continue;
            }
            AimingData aimingData = new AimingData(this, enemy, firingPosition, firePower, expectedMoves);
            return aimingData;
        }
        return null;
    }

    private Point.Double getFiringPosition(Enemy target, double firePower, List<Move> movesLog, List<Point.Double> predMoves) {
        double bulletSpeed = getBulletSpeed(firePower);
        Point.Double firePoint = clonePoint(target);

        for (int i = 0; i < 5; i++) {
            double distance = target.getGpBase().getCurrentPoint().distance(firePoint);
            long time = (long) (distance / bulletSpeed);
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


                try {
                    double x = target.getGpBase().ensureXInBatleField(firePoint.x + dist * cos(dir), 2.1);
                    double y = target.getGpBase().ensureYInBatleField(firePoint.y + dist * sin(dir), 2.1);
                    firePoint.x = x;
                    firePoint.y = y;
                } catch (Exception e) {
                    continue;
                }

                predMoves.add(clonePoint(firePoint));
                moveDuration += m.getDuration();
            }
        }

        return firePoint;
    }
}
