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
        if (target.getPatternKdTree() == null) return null;

        double[] kdPoint = getPatternPoint(target);
        List<KdTree.Entry<List<Move>>> el = target.getPatternKdTree().nearestNeighbor(kdPoint, 10, true);

        return getKdTreeAimingData(target, el);
    }

    @Override
    public Color getColor() {
        return Color.RED;
    }

}
