package tankbase.gun.kdformula;

import tankbase.AbstractTankBase;
import tankbase.ITank;
import tankbase.TankState;
import tankbase.kdtree.KdTree;

import static java.lang.Math.PI;
import static java.lang.Math.min;
import static robocode.Rules.DECELERATION;
import static robocode.Rules.MAX_TURN_RATE_RADIANS;
import static robocode.Rules.MAX_VELOCITY;
import static robocode.util.Utils.normalAbsoluteAngle;
import static tankbase.AbstractTankBase.DISTANCE_MAX;
import static tankbase.TankUtils.directToWallDistance;

public class Cluster extends AbstractKDFormula {
    double[] weights = {1, 1, 1, 1, 1, 1, 1};
    ITank target;

    public Cluster(ITank target, AbstractTankBase base) {
        super(base);
        this.target = target;
        kdTree = new KdTree.WeightedSqrEuclid<>(weights.length, 1000);
        kdTree.setWeights(weights);
    }

    @Override
    public double[] getPoint() {
        TankState state = target.getState();
        return new double[]{
                state.getVelocity() / MAX_VELOCITY,
                (normalAbsoluteAngle(state.getHeadingRadians()) % (PI / 2)) / (PI / 2),
                state.getTurnRate() / MAX_TURN_RATE_RADIANS,
                directToWallDistance(state, state.getHeadingRadians()) / DISTANCE_MAX,
                directToWallDistance(state, state.getHeadingRadians() + PI) / DISTANCE_MAX,
                state.getAcceleration() / DECELERATION,
                min(1.0, (state.getTime() - target.getLastStop()) / 15.0)
        };
    }
}
