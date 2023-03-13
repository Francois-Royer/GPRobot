package gpbase.gun;

import gpbase.Enemy;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static gpbase.GPBase.ensureXInBatleField;
import static gpbase.GPBase.ensureYInBatleField;
import static gpbase.GPUtils.checkMinMax;
import static gpbase.GPUtils.clonePoint;
import static java.lang.Math.*;
import static robocode.Rules.*;

public class CircularGunner extends HeadOnGunner {

    public CircularGunner() {
        super();
    }

    private Point.Double forwardMovementPrediction(Enemy target,
                                                          List<Point.Double> predMoves, double firePower) {
        Point.Double from = target.getGpBase().getCurrentPoint();
        double bulletSpeed = getBulletSpeed(firePower);
        Point.Double firePoint = target;

        if (target.getEnergy() > 0)
            for (int i = 0; i < 3; i++) {
                long time = (long) (from.distance(firePoint) / bulletSpeed);
                firePoint = clonePoint(target);
                double direction = target.getDirection();
                double v = target.getVelocity();

                predMoves.clear();

                for (long t = 0; t < time; t++) {
                    v = checkMinMax(v + ACCELERATION, 0, min(target.getvMax(), MAX_VELOCITY));
                    direction += min(abs(target.getRotationRate()), getTurnRateRadians(v)) * signum(target.getRotationRate());

                    try {
                        firePoint.x = ensureXInBatleField(firePoint.x + v * cos(direction));
                    } catch (Exception e) { // Hitwall
                    }
                    try {
                        firePoint.y = ensureYInBatleField(firePoint.y + v * sin(direction));
                    } catch (Exception e) {// Hitwall
                    }
                    predMoves.add(clonePoint(firePoint));
                }
            }

        return firePoint;
    }

    @Override
    public AimingData aim(Enemy enemy) {
        double firePower = getFirePower(enemy);

        List<Point.Double> predMoves = new ArrayList<>();
        Point.Double firingPosition = forwardMovementPrediction(enemy, predMoves, firePower);

        return new AimingData(this, enemy, firingPosition, firePower, predMoves);
    }
}
