package tankbase.gun.kdformula;

import tankbase.Move;
import tankbase.kdtree.KdTree;

import java.util.List;

public interface KDFormula {
    KdTree<List<Move>> getKdTree();

    void addPoint(double[] point, List<Move> moveList);

    double[] getPoint();
}
