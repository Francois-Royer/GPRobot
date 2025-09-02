package tankbase.gun;

import tankbase.ITank;
import tankbase.KDMove;
import tankbase.Move;
import tankbase.kdtree.KdTree;

import java.awt.*;
import java.util.List;

public class AntiSurferGun extends AbstractKdTreeGun {

    public AntiSurferGun(ITank tank) {
        super(tank);
    }

    @Override
    public Aiming aim(ITank target) {
        if (target.getSurferFormula() == null) return null;

        double[] kdPoint = target.getSurferFormula().getPoint(target.getState());
        List<KdTree.Entry<List<Move>>> el = target.getSurferFormula().getKdTree().nearestNeighbor(kdPoint, 10, true);

        return getKdTreeAimingData(target, el);
    }

    @Override
    public Color getColor() {
        return Color.YELLOW;
    }
}
