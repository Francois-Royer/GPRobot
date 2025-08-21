package tankbase.gun;

import tankbase.ITank;
import tankbase.Move;
import tankbase.kdtree.KdTree;

import java.awt.*;
import java.util.List;

public class SurferGunner extends AbstractKdTreeGunner {

    public SurferGunner(ITank tank) {
        super(tank);
    }

    @Override
    public AimingData aim(ITank target) {
        if (target.getPatternKdTree() == null) return null;

        double[] kdPoint = getSurferPoint(target, getTank());
        List<KdTree.Entry<List<Move>>> el = target.getSurferKdTree().nearestNeighbor(kdPoint, 10, true);

        return getKdTreeAimingData(target, el);
    }

    @Override
    public Color getColor() {
        return Color.YELLOW;
    }
}
