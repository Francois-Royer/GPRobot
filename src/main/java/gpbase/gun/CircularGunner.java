package gpbase.gun;

import gpbase.Enemy;
import gpbase.GPBase;
import robocode.Rules;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static gpbase.GPUtils.*;
import static java.lang.Math.*;
import static robocode.Rules.*;

import static gpbase.GPBase.*;
public class CircularGunner extends HeadOnGunner {

    public CircularGunner(GPBase gpbase) {
        super(gpbase);
    }

    @Override
    public AimingData aim(Enemy enemy) {
        double firePower = getFirePower(enemy);

        List<Point.Double> predMoves = new ArrayList<>();
        Point.Double firingPosition = forwardMovementPrediction(enemy, gpbase.getCurrentPoint(), predMoves, firePower);

        return new AimingData(this, enemy, firingPosition, firePower, predMoves);
    }

     static private Point.Double forwardMovementPrediction(Enemy target,  Point.Double from,
                                                           List<Point.Double>predMoves, double firePower) {
        double bulletSpeed = getBulletSpeed(firePower);

        Point.Double firePoint = null;
        for (int i=0 ; i<5 ; i++) {
            firePoint = clonePoint(target);
            long time = (long) (from.distance(firePoint) / bulletSpeed);
            double direction = target.getDirection();
            double v = target.getVelocity();

            predMoves.clear();

            for (long t = 0; t < time; t++) {
                v = checkMinMax(v + Rules.ACCELERATION, 0, MAX_VELOCITY);
                direction += min(abs(target.getRotationRate()), getTurnRateRadians(v))* signum(target.getRotationRate());

                try {
                    double x = ensureXInBatleField(firePoint.x + v * cos(direction));
                    double y = ensureYInBatleField(firePoint.y + v * sin(direction));

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
