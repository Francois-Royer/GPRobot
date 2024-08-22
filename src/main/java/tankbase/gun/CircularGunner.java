package tankbase.gun;

import tankbase.ITank;
import tankbase.TankUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.*;
import static robocode.Rules.*;
import static tankbase.TankBase.TANK_SIZE;
import static tankbase.TankUtils.checkMinMax;
import static tankbase.TankUtils.clonePoint;

public class CircularGunner extends AbtractGunner {

    public CircularGunner(ITank tank) {
        super(tank);
    }

    @Override
    public AimingData aim(ITank target) {
        if (target.getVelocity() == 0 && (target.getAccel() == 0 || target.getAccel() == -8))
            return null;

        double firePower = getFirePower(target);

        List<Point.Double> predMoves = new ArrayList<>();
        Point.Double[] firingPosition = null;
        while (firePower >= MIN_BULLET_POWER && firingPosition == null) {
            firingPosition = forwardMovementPrediction(target, predMoves, firePower);
            firePower -= .1;
        }

        if (firingPosition == null) return null;

        return new AimingData(this, target, firingPosition[0], firingPosition[0], firePower, predMoves);
    }

    @Override
    public Color getColor() {
        return Color.GREEN;
    }

    private Point.Double[] forwardMovementPrediction(ITank target, List<Point.Double> predMoves, double firePower) {
        Point.Double from = getTank().getPosition();
        double bulletSpeed = getBulletSpeed(firePower);
        Point.Double firePoint = target.getPosition();
        Point.Double prevPoint = target.getPosition();
        long prevTime = 0;
        long prevDelta = Long.MAX_VALUE;

        for (int i = 0; i < 10; i++) {
            long time = (long) (from.distance(firePoint) / bulletSpeed);

            if (prevTime == time || abs(time - prevTime) > prevDelta)
                break;

            prevDelta = abs(time - prevTime);
            prevTime = time;
            firePoint = clonePoint(target.getPosition());
            double direction = target.getHeadingRadians();
            double v = target.getVelocity();

            predMoves.clear();

            for (long t = 0; t < time + 1; t++) {
                double accel = target.getAccel();
                v = checkMinMax(v + accel, max(target.getVMin(), -MAX_VELOCITY), min(target.getVMax(), MAX_VELOCITY));

                direction += min(abs(target.getTurnRate()), getTurnRateRadians(v)) * signum(target.getTurnRate());

                if (t == time)
                    prevPoint = clonePoint(firePoint);

                firePoint.x += v * cos(direction);
                firePoint.y += v * sin(direction);


                if (!TankUtils.pointInBattleField(firePoint, TANK_SIZE / 2.1))
                    return null;

                predMoves.add(clonePoint(firePoint));
            }
        }

        return new Point.Double[]{prevPoint, firePoint};
    }
}
