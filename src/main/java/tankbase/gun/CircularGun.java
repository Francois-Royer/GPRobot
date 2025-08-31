package tankbase.gun;

import tankbase.ITank;
import tankbase.TankState;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.abs;
import static robocode.Rules.*;

public class CircularGun extends AbtractGun {

    public CircularGun(ITank tank) {
        super(tank);
    }

    @Override
    public Aiming aim(ITank target) {
        if (target.getState().getVelocity() == 0 && (target.getState().getAcceleration() == 0 ||
                target.getState().getAcceleration() == -MAX_VELOCITY))
            return null;

        double firePower = getFirePower(target);

        List<Point2D.Double> predMoves = new ArrayList<>();
        Point2D.Double[] firingPosition = null;
        while (firePower >= MIN_BULLET_POWER) {
            firingPosition = forwardMovementPrediction(target, predMoves, firePower);
            if (firingPosition != null)
                break;
            firePower -= .1;
        }

        if (firingPosition == null) return null;

        return new Aiming(this, target, firingPosition[0], firingPosition[1], firePower, predMoves);
    }

    @Override
    public Color getColor() {
        return Color.GREEN;
    }

    private Point2D.Double[] forwardMovementPrediction(ITank target, List<Point2D.Double> predMoves, double firePower) {
        Point2D.Double from = getFirer().getState();
        double bulletSpeed = getBulletSpeed(firePower);
        TankState targetState = target.getState();
        Point2D.Double prevPoint = targetState;
        long prevTime = 0;
        long prevDelta = Long.MAX_VALUE;

        for (int i = 0; i < 10; i++) {
            long time = (long) (from.distance(targetState) / bulletSpeed);

            if (prevTime == time || abs(time - prevTime) > prevDelta)
                break;

            targetState = target.getState();
            prevDelta = abs(time - prevTime);
            prevTime = time;
            predMoves.clear();

            for (long t = 0; t < time + 1; t++) {
                prevPoint = targetState;
                targetState = targetState.extrapolateNextState();

                if (targetState == null)
                    return null;

                predMoves.add(targetState);
            }
        }

        predMoves.remove(targetState);
        predMoves.remove(prevPoint);
        return new Point2D.Double[]{prevPoint, targetState};
    }
}
