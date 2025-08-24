package tankbase.gun.kdFormula;

import tankbase.ITank;
import tankbase.Move;
import tankbase.kdtree.KdTree;

import java.util.List;

import static java.lang.Math.PI;
import static java.lang.Math.min;
import static robocode.Rules.DECELERATION;
import static robocode.Rules.MAX_TURN_RATE_RADIANS;
import static robocode.Rules.MAX_VELOCITY;
import static tankbase.AbstractTankBase.DISTANCE_MAX;
import static tankbase.TankUtils.wallIntersection;

public class Pattern implements KDFormula {
    KdTree.WeightedManhattan kdTree;
    ITank target;
    double[] weights = {1, 1, 1, 2, 2, 3, 3, 3};

    public Pattern(ITank target) {
        this.target = target;
        kdTree = new KdTree.WeightedManhattan<>(weights.length, KDTREE_MAX_SIZE);
        kdTree.setWeights(weights);
    }

    @Override
    public KdTree<List<Move>> getKdTree() {
        return kdTree;
    }

    @Override
    public void addPoint(double []point, List<Move> moveList) {
        kdTree.addPoint(point, moveList);
    }

    @Override
    public double[] getPoint() {
        return new double[] {
                target.getState().getVelocity() / MAX_VELOCITY,
                target.getState().getHeadingRadians()%(PI/2),
                target.getState().getPosition().distance(wallIntersection(target.getState().getPosition(), target.getState().getMovingDirection())) / DISTANCE_MAX,
                target.getState().getTurnRate() / MAX_TURN_RATE_RADIANS,
                target.getState().getAcceleration() / DECELERATION,
                min(target.getState().getTime()-target.getLastStop(), 100)/100,
                min(target.getState().getTime()-target.getLastChangeDirection(), 100)/100,
                min(target.getState().getTime()-target.getLastVelocityChange(), 100)/100
        };
    }
}
