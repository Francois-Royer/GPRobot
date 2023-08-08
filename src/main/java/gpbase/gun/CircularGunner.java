package gpbase.gun;

import gpbase.Enemy;
import robocode.Rules;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static gpbase.GPBase.*;
import static gpbase.GPUtils.checkMinMax;
import static gpbase.GPUtils.clonePoint;
import static java.lang.Math.*;
import static robocode.Rules.*;

public class CircularGunner extends AbtractGunner {

    @Override
    public AimingData aim(Enemy enemy) {
        double firePower = getFirePower(enemy);

        List<Point.Double> predMoves = new ArrayList<>();
        Point.Double firingPosition = null;
        while (firePower >= MIN_BULLET_POWER && firingPosition == null) {
            firingPosition = forwardMovementPrediction(enemy, predMoves, firePower);
            firePower -= .1;
        }

        if (firingPosition == null) return null;

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
                    double accel = target.getAccel();
                    v = checkMinMax(v + accel, max(target.getvMin(), -MAX_VELOCITY), min(target.getvMax(), MAX_VELOCITY));
                    if (v == 0)
                        return null;

                    direction += min(abs(target.getRotationRate()), getTurnRateRadians(v)) * signum(target.getRotationRate());

                    firePoint.x += v * cos(direction);
                    firePoint.y += v * sin(direction);

                    if (! pointInBattleField(firePoint,  (double) TANK_SIZE / 2.0001))
                        return null;

                    predMoves.add(clonePoint(firePoint));
                }
            }

        return firePoint;
    }
}
