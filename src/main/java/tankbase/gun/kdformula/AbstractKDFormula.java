package tankbase.gun.kdformula;

import tankbase.AbstractTankBase;
import tankbase.Move;
import tankbase.kdtree.KdTree;

import java.util.List;

abstract public class AbstractKDFormula implements KDFormula {
    protected KdTree.WeightedSqrEuclid<List<Move>> kdTree;
    protected AbstractTankBase base;

    public AbstractKDFormula(AbstractTankBase base) {
        this.base = base;
    }

    @Override
    public KdTree<List<Move>> getKdTree() {
        return kdTree;
    }

    @Override
    public void addPoint(double[] point, List<Move> moveList) {
        kdTree.addPoint(point, moveList);
    }


}
