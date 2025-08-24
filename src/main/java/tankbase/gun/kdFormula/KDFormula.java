package tankbase.gun.kdFormula;

import tankbase.Move;
import tankbase.kdtree.KdTree;

import java.util.List;

public interface KDFormula {
    int KDTREE_MAX_SIZE = 1000;

    KdTree<List<Move>> getKdTree();
    void addPoint(double[] point, List<Move> moveList);
    double[] getPoint();
}
