package tankbase.gun;

import tankbase.ITank;
import tankbase.KDMove;
import tankbase.Move;
import tankbase.kdtree.KdTree;

import java.awt.*;
import java.util.List;

public class ClusterGun extends AbstractKdTreeGun {

    public ClusterGun(ITank tank) {
        super(tank);
    }

    @Override
    public Aiming aim(ITank target) {
        if (target.getPatternFormula() == null) return null;

        double[] kdPoint = target.getPatternFormula().getPoint(target.getState());
        List<KdTree.Entry<List<Move>>> el = target.getPatternFormula().getKdTree().nearestNeighbor(kdPoint, 10, true);

        return getKdTreeAimingData(target, el);
    }

    @Override
    public Color getColor() {
        return Color.RED;
    }

}
