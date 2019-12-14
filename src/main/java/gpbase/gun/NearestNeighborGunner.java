package gpbase.gun;

import gpbase.Enemy;
import gpbase.GPBase;
import gpbase.Move;
import gpbase.dataStructures.trees.KD.NearestNeighborIterator;
import gpbase.dataStructures.trees.KD.SquareEuclideanDistanceFunction;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static gpbase.GPUtils.*;
import static java.lang.Math.*;
import static robocode.Rules.*;

public class NearestNeighborGunner extends AbtractGunner {
    private static double KD_DISTANCE_MAX=100; // used to compute confidence
    GPBase gpbase;

    public NearestNeighborGunner(GPBase gpbase) {
        this.gpbase = gpbase;
    }


    @Override
    public AimingData aim(Enemy target) {
        if (target.getKdtree() == null) return null;

        NearestNeighborIterator<List<Move>> it = target.getKdtree().getNearestNeighborIterator(target.getKDPoint(gpbase), 1, new SquareEuclideanDistanceFunction());
        if (it.hasNext()) {
            List<Move> movesLog = it.next();
            double distance = it.distance();
            //gpbase.out.printf("%s kddistance = %f\n", target.getName(), distance);
            double confidence = getConfidence(distance);
            double firePower = firePowerFromConfidenceAndEnergy(confidence, gpbase.getEnergy());
            List<Point.Double> expectedMoves = new ArrayList<>();
            Point.Double firingPosition = getFiringPosition(target, firePower, movesLog, expectedMoves);
            return new AimingData(this, target, firingPosition, firePower, expectedMoves, confidence);
        }
        return null;
    }

    private Point.Double getFiringPosition(Enemy target, double firePower, List<Move> movesLog, List<Point.Double> predMoves) {
        double bulletSpeed = getBulletSpeed(firePower);
        Point.Double firePoint = clonePoint(target);;
        for (int i=0 ; i<5 ; i++) {
            double distance = gpbase.getCurrentPoint().distance(firePoint);
            long time = (long) (distance / bulletSpeed);
            firePoint = clonePoint(target);
            predMoves.clear();
            long moveDuration = 0;
            int step = 1;
            double dir = target.getDirection();

            while (moveDuration < time) {
                Move m = movesLog.get(step++);
                moveDuration += m.getDuration();
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
                } catch (Exception e) {
                    // Hitwall
                }
            }
        }

        return firePoint;
    }

    private double getConfidence(double distance) {
        return checkMinMax(range(distance, 0, KD_DISTANCE_MAX, 1, 0), 0.05, 1);
    }
}
