package gpbase.gun;

import gpbase.Enemy;
import gpbase.GPBase;

import java.awt.*;
import java.awt.geom.Point2D;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static gpbase.GPUtils.*;
import static java.lang.Math.*;
import static robocode.Rules.*;

public class CircularGunner extends AbtractGunner {
    GPBase gpbase;

    public CircularGunner(GPBase gpbase) {
        this.gpbase = gpbase;
    }


    @Override
    public AimingData aim(Enemy enemy) {
        double confidence = getConfidence(enemy);

        List<Point.Double> predMoves = new ArrayList<>();
        double firePower = firePowerFromConfidence(confidence);
        Point.Double firingPosition = forwardMovementPrediction(enemy, predMoves, firePower);

        return new AimingData(enemy, firingPosition, firePower, predMoves, confidence);
    }

    public double getConfidence(Enemy enemy) {
        double confidence =
                range(enemy.getVelocityVariance(), 0, enemy.getVelocityVarianceMax(), 1,0) *
                        range(enemy.getTurnVariance(), 0, enemy.getTurnVarianceMax(), 1,0);

        // distance decrease confidence
        double distance= gpbase.getCurrentPoint().distance(enemy);
        double diffOne = 1-confidence;
        confidence -= diffOne*range(distance, gpbase.dmin, gpbase.dmax, 0,2);
        return checkMinMax(confidence, 0.001, 1);
    }

     private Point.Double forwardMovementPrediction(Enemy target,  List<Point.Double>predMoves, double firePower) {
        double bulletSpeed = getBulletSpeed(firePower);

        Point.Double firePoint = clonePoint(target);
        for (int i=0 ; i<5 ; i++) {
            long time = (long) (gpbase.getCurrentPoint().distance(firePoint) / bulletSpeed);
            firePoint = clonePoint(target);
            double direction = target.getDirection();
            double v = target.getVelocity();

            predMoves.clear();

            for (long t = 0; t < time; t++) {

                v = checkMinMax(v + target.getAccel(), target.getvMin(), target.getvMax());
                direction += min(abs(target.getRotationRate()), getTurnRateRadians(v))* signum(target.getRotationRate());

                try {
                    double x = gpbase.ensureXInBatleField(firePoint.x + v * cos(direction));
                    double y = gpbase.ensureYInBatleField(firePoint.y + v * sin(direction));

                    firePoint.x = x;
                    firePoint.y = y;
                } catch (Exception e) {
                    // Hitwall
                }
                predMoves.add(clonePoint(firePoint));
            }
        }

        return firePoint;
    }

}
