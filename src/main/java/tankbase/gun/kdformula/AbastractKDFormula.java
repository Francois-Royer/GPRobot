package tankbase.gun.kdformula;

import tankbase.ITank;
import tankbase.Move;
import tankbase.kdtree.KdTree;

import java.util.List;

abstract public class AbastractKDFormula implements KDFormula {
    KdTree.WeightedSqrEuclid<List<Move>> kdTree;

    @Override
    public KdTree<List<Move>> getKdTree() {
        return kdTree;
    }

    @Override
    public void addPoint(double[] point, List<Move> moveList) {
        kdTree.addPoint(point, moveList);
    }


}
