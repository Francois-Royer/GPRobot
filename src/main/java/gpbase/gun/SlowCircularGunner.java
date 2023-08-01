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

public class SlowCircularGunner extends AbtractGunner {

    @Override
    public AimingData aim(Enemy enemy) {
        double firePower = getFirePower(enemy);

        List<Point.Double> predMoves = new ArrayList<>();
        Point.Double firingPosition = forwardMovementPrediction(enemy, predMoves, firePower);

        return new AimingData(this, enemy, firingPosition, firePower, predMoves);
    }

    @Override
    public Color getColor() { return Color.GREEN; }

    private Point.Double forwardMovementPrediction(Enemy target,
                                                   List<Point.Double> predMoves, double firePower) {
        Point.Double from = target.getGpBase().getCurrentPoint();
        double bulletSpeed = getBulletSpeed(firePower);
        Point.Double firePoint = target;
        long prevTime=0;
        long prevDelta=Long.MAX_VALUE;

        if (target.getEnergy() > 0)
            for (int i = 0; i < 10; i++) {
                long time = (long) (from.distance(firePoint) / bulletSpeed);

                if (prevTime == time || abs(time-prevTime) >= prevDelta)
                    break;

                prevDelta=abs(time-prevTime);
                prevTime = time;
                firePoint = clonePoint(target);
                double direction = target.getDirection();
                double v = target.getVelocity();

                predMoves.clear();

                for (long t = 0; t < time; t++) {

                    v = checkMinMax(v + target.getAccel(), max(target.getvMin(), -MAX_VELOCITY/3), min(target.getvMax(), MAX_VELOCITY/3));
                    direction += min(abs(target.getRotationRate()), getTurnRateRadians(v)) * signum(target.getRotationRate());

                    try {
                        double x = ensureXInBatleField(firePoint.x + v * cos(direction));
                        double y = ensureYInBatleField(firePoint.y + v * sin(direction));

                        firePoint.x=x;
                        firePoint.y=y;

                    } catch (Exception e) { // Hitwall
                    }
                    predMoves.add(clonePoint(firePoint));
                }
            }

        return firePoint;
    }
}
