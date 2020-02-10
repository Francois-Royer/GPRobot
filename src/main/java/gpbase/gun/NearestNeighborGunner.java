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
    }

    @Override
    public AimingData aim(Enemy target) {
        if (target.getKdTree() == null) return null;

        double[] kdPoint = target.getKDPoint(gpbase);
        kdPoint[0] = 100;
        NearestNeighborIterator<List<Move>> it = target.getKdTree().getNearestNeighborIterator(kdPoint, 20, new SquareEuclideanDistanceFunction());

        while (it.hasNext()) {
            KdEntry<List<Move>> kdEntry = it.next();
            if (kdEntry.isDeleted()) continue;
            double dist = it.distance();
            List<Move> movesLog = kdEntry.getData();
            double confidence = getConfidence(target);
            double firePower = firePowerFromConfidenceAndEnergy(confidence, gpbase.getEnergy());
            List<Point.Double> expectedMoves = new ArrayList<>();
            Point.Double firingPosition = getFiringPosition(target, firePower, movesLog, expectedMoves);
            if (firingPosition == null)
                continue;
            AimingData aimingData = new AimingData(this, target, firingPosition, firePower, expectedMoves, confidence, kdEntry.getCoordinates());
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

    public double getConfidence(Enemy enemy) {
        if (enemy.getEnergy() == 0) return 1;
        double distanceFactor = range(gpbase.getCurrentPoint().distance(enemy), GPBase.dmin, GPBase.dmax, -1, 1) * 4;
        double varianceConfidence = (range(enemy.getVelocityVariance(), 0, enemy.getVelocityVarianceMax(), 1, 0) +
            range(enemy.getTurnVariance(), 0, enemy.getTurnVarianceMax(), 1, 0)) / 2;

        double dist2One = 1 - varianceConfidence;
        return checkMinMax(varianceConfidence - dist2One * distanceFactor, 0.05, 1);
    }
}
