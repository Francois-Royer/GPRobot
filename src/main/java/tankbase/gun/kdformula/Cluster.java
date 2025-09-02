package tankbase.gun.kdformula;

import tankbase.ITank;
import tankbase.KDMove;
import tankbase.Move;
import tankbase.TankState;
import tankbase.kdtree.KdTree;

import java.util.List;

import static java.lang.Math.PI;
import static java.lang.Math.min;
import static robocode.Rules.*;
import static robocode.util.Utils.normalAbsoluteAngle;
import static tankbase.AbstractTankBase.DISTANCE_MAX;
import static tankbase.TankUtils.wallIntersection;

public class Cluster extends AbastractKDFormula {
    double[] weights = {1, 1, 1, 1, 1, 1, 1, 1};
    ITank target;

    public Cluster(ITank target) {
        this.target = target;
        kdTree = new KdTree.WeightedSqrEuclid<>(weights.length, 1000);
        kdTree.setWeights(weights);
    }

    @Override
    public double[] getPoint() {
        TankState state = target.getState();
        return new double[]{
                state.getVelocity() / MAX_VELOCITY,
                ((normalAbsoluteAngle(state.getHeadingRadians()) - .001) % (PI / 4)) / (PI / 4),
                state.getTurnRate() / MAX_TURN_RATE_RADIANS,
                state.distance(wallIntersection(state, state.getMovingDirection())) / DISTANCE_MAX,
                state.getAcceleration() / DECELERATION,
                (double) min(state.getTime() - target.getLastStop(), 100) / 100,
                (double) min(state.getTime() - target.getLastChangeDirection(), 100) / 100,
                (double) min(state.getTime() - target.getLastVelocityChange(), 100) / 100
        };
    }
}
