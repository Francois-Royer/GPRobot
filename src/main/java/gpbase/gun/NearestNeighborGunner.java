package gpbase.gun;

import gpbase.Enemy;
import gpbase.GPBase;
import gpbase.Move;
import gpbase.kdtree.KdEntry;
import gpbase.kdtree.NearestNeighborIterator;
import gpbase.kdtree.SquareEuclideanDistanceFunction;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static gpbase.GPUtils.*;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static robocode.Rules.getBulletSpeed;

public class NearestNeighborGunner extends AbtractGunner {
    private static double KD_DISTANCE_MAX = 100; // used to compute confidence
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
        NearestNeighborIterator<List<Move>> it = enemy.getKdTree().getNearestNeighborIterator(kdPoint, 20, new SquareEuclideanDistanceFunction());

        while (it.hasNext()) {
            KdEntry<List<Move>> kdEntry = it.next();
            if (kdEntry.isDeleted()) continue;
            double dist = it.distance();
            List<Move> movesLog = kdEntry.getData();
            double firePower = getFirePower(enemy);
            List<Point.Double> expectedMoves = new ArrayList<>();
            Point.Double firingPosition = getFiringPosition(enemy, firePower, movesLog, expectedMoves);
            if (firingPosition == null)
                continue;
            AimingData aimingData = new AimingData(this, enemy, firingPosition, firePower, expectedMoves, kdEntry.getCoordinates());
            //System.out.printf("Square Euclidean distance=%f\n", dist);
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
                    double x = gpbase.ensureXInBatleField(firePoint.x + dist * cos(dir));
                    double y = gpbase.ensureYInBatleField(firePoint.y + dist * sin(dir));

                    firePoint.x = x;
                    firePoint.y = y;
                    predMoves.add(clonePoint(firePoint));
                    moveDuration += m.getDuration();
                } catch (Exception e) {
                    return null;
                }
            }
        }

        return firePoint;
    }
}
