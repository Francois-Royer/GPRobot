package tankbase.gun.kdformula;

import tankbase.ITank;
import tankbase.Move;
import tankbase.TankState;
import tankbase.kdtree.KdTree;

import java.util.List;

import static java.lang.Math.PI;
import static java.lang.Math.min;
import static robocode.Rules.DECELERATION;
import static robocode.Rules.MAX_TURN_RATE_RADIANS;
import static robocode.Rules.MAX_VELOCITY;
import static robocode.util.Utils.normalAbsoluteAngle;
import static tankbase.AbstractTankBase.DISTANCE_MAX;
import static tankbase.TankUtils.wallIntersection;

public class Pattern implements KDFormula {
    KdTree.WeightedSqrEuclid<List<Move>> kdTree;
    ITank target;
    double[] weights = {1, 1, 1, 1, 1, 1, 1, 1};

    public Pattern(ITank target) {
        this.target = target;
        kdTree = new KdTree.WeightedSqrEuclid<>(weights.length, KDTREE_MAX_SIZE);
        kdTree.setWeights(weights);
    }

    @Override
    public KdTree<List<Move>> getKdTree() {
        return kdTree;
    }

    @Override
    public void addPoint(double[] point, List<Move> moveList) {
        kdTree.addPoint(point, moveList);
    }

    @Override
    public double[] getPoint(TankState state) {
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
