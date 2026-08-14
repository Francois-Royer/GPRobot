package tankbase.gun.kdformula;

import tankbase.AbstractTankBase;
import tankbase.ITank;
import tankbase.TankState;
import tankbase.kdtree.KdTree;

import static java.lang.Math.PI;
import static java.lang.Math.min;
import static robocode.Rules.DECELERATION;
import static robocode.Rules.MAX_BULLET_POWER;
import static robocode.Rules.MAX_TURN_RATE_RADIANS;
import static robocode.Rules.MAX_VELOCITY;
import static robocode.Rules.getBulletSpeed;
import static tankbase.AbstractTankBase.DISTANCE_MAX;
import static tankbase.TankUtils.directToWallDistance;

public class AntiSurfer extends AbstractKDFormula {
    double MAX_BULLET_SPEED = getBulletSpeed(MAX_BULLET_POWER);
    double[] weights = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
    ITank target;
    ITank firer;

    public AntiSurfer(ITank target, AbstractTankBase base) {
        super(base);
        this.target = target;
        this.firer = base;
        kdTree = new KdTree.WeightedSqrEuclid<>(weights.length, 256);
        kdTree.setWeights(weights);
    }

    public double[] getPoint() {
        TankState state = target.getState();

        return new double[]{
                state.getVelocity() / MAX_VELOCITY,
                state.getTurnRate() / MAX_TURN_RATE_RADIANS,
                directToWallDistance(state, state.getHeadingRadians()) / DISTANCE_MAX,
                directToWallDistance(state, state.getHeadingRadians() + PI) / DISTANCE_MAX,
                state.getAcceleration() / DECELERATION,
                min(1.0, (state.getTime() - target.getLastStop()) / 15.0),
                min(1.0, (state.getTime() - target.getLastChangeDirection()) / 15.0),
                min(1.0, (state.getTime() - target.getLastVelocityChange()) / 15.0),
                state.distance(firer.getState()) / DISTANCE_MAX,

                base.bulletPos(0).distance(state) / DISTANCE_MAX,
                base.bulletPower(0) / MAX_BULLET_POWER,
                (base.bulletHeading(0) - state.getHeadingRadians()) % (PI) / (PI),
                /*,

                base.bulletPos(1).distance(state)/DISTANCE_MAX,
                base.bulletPower(1)/ MAX_BULLET_POWER,

                base.bulletPos(2).distance(state)/DISTANCE_MAX,
                base.bulletPower(2)/ MAX_BULLET_POWER,*/
        };
    }
}
