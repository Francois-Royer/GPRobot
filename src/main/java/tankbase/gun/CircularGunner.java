package tankbase.gun;

import tankbase.ITank;
import tankbase.TankState;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.abs;
import static robocode.Rules.MAX_VELOCITY;
import static robocode.Rules.MIN_BULLET_POWER;
import static robocode.Rules.getBulletSpeed;
import static tankbase.AbstractTankBase.sysout;

public class CircularGunner extends AbtractGunner {

    public CircularGunner(ITank tank) {
        super(tank);
    }

    @Override
    public AimingData aim(ITank target) {
        if (target.getState().getVelocity() == 0 && (target.getState().getAcceleration() == 0 ||
                target.getState().getAcceleration() == -MAX_VELOCITY))
            return null;

        double firePower = getFirePower(target);

        List<Point.Double> predMoves = new ArrayList<>();
        Point.Double[] firingPosition = null;
        while (firePower >= MIN_BULLET_POWER ) {
            firingPosition = forwardMovementPrediction(target, predMoves, firePower);
            if (firingPosition != null)
                break;
            firePower -= .1;
        }

        if (firingPosition == null) return null;

        return new AimingData(this, target, firingPosition[0], firingPosition[1], firePower, predMoves);
    }

    @Override
    public Color getColor() {
        return Color.GREEN;
    }

    private Point.Double[] forwardMovementPrediction(ITank target, List<Point.Double> predMoves, double firePower) {
        Point.Double from = getGunner().getState().getPosition();
        double bulletSpeed = getBulletSpeed(firePower);
        TankState targetState = target.getState();
        Point.Double prevPoint = targetState.getPosition() ;
        long prevTime = 0;
        long prevDelta = Long.MAX_VALUE;

        for (int i = 0; i < 10; i++) {
            long time = (long) (from.distance(targetState.getPosition()) / bulletSpeed);

            if (prevTime == time || abs(time - prevTime) > prevDelta)
                break;

            targetState = target.getState();
            prevDelta = abs(time - prevTime);
            prevTime = time;
            predMoves.clear();

            for (long t = 0; t < time + 1; t++) {
                prevPoint = targetState.getPosition();
                targetState = targetState.extrapolateNextState();

                if (targetState == null)
                    return null;

                predMoves.add(targetState.getPosition());
            }
            predMoves.remove(targetState.getPosition());
        }

        return new Point.Double[]{prevPoint, targetState.getPosition()};
    }
}
