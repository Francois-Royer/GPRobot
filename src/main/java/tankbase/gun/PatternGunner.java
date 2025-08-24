package tankbase.gun;

import tankbase.ITank;
import tankbase.Move;
import tankbase.kdtree.KdTree;

import java.awt.*;
import java.util.List;

public class PatternGunner extends AbstractKdTreeGunner {

    public PatternGunner(ITank tank) {
        super(tank);
    }

    @Override
    public AimingData aim(ITank target) {
        if (target.getPatternFormula() == null) return null;

        double[] kdPoint = target.getPatternFormula().getPoint();
        List<KdTree.Entry<List<Move>>> el = target.getPatternFormula().getKdTree().nearestNeighbor(kdPoint, 10, true);

        return getKdTreeAimingData(target, el);
    }

    @Override
    public Color getColor() {
        return Color.RED;
    }

}
