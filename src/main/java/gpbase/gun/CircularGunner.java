package gpbase.gun;

import gpbase.Enemy;
import gpbase.GPBase;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static gpbase.GPUtils.*;
import static java.lang.Math.*;
import static robocode.Rules.*;

public class CircularGunner extends HeadOnGunner {

    public CircularGunner(GPBase gpbase) {
        super(gpbase);
    }

    @Override
    public AimingData aim(Enemy enemy) {
        double firePower = getFirePower(enemy);

        List<Point.Double> predMoves = new ArrayList<>();
        Point.Double firingPosition = forwardMovementPrediction(enemy, predMoves, firePower);

        return new AimingData(this, enemy, firingPosition, firePower, predMoves);
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
