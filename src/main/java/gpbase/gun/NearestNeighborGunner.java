package gpbase.gun;

import gpbase.Enemy;
import gpbase.GPBase;
import gpbase.Move;
import gpbase.kdtree.KdTree;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static gpbase.GPUtils.*;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static robocode.Rules.getBulletSpeed;

public class NearestNeighborGunner extends AbtractGunner {
    GPBase gpbase;

    public NearestNeighborGunner(GPBase gpbase) {
        this.gpbase = gpbase;
        setResetStat(true);
    }

    @Override
    public AimingData aim(Enemy enemy) {
        if (enemy.getKdTree() == null) return null;

        double[] kdPoint = enemy.getKDPoint(gpbase);
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
            double distance = gpbase.getCurrentPoint().distance(firePoint);
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
                    double x = gpbase.ensureXInBatleField(firePoint.x + dist * cos(dir), 2.1);
                    double y = gpbase.ensureYInBatleField(firePoint.y + dist * sin(dir), 2.1);
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

    @Override
    public void resetStat(Enemy enemy) {
        super.resetStat(enemy);
        //if (enemy.getKdTree() != null)
            //System.out.printf("Kd size=%d\n", enemy.getKdTree().size());
    }
}
