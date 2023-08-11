package tankbase;

import tankbase.kdtree.KdTree;

import java.awt.*;
import java.util.List;

public interface ITank {
    String getName();

    double getVelocity();

    double getHeadingRadians();

    double getEnergy();

    double getVMax();

    double getVMin();

    double getTurnRate();

    double getAccel();

    Point.Double getPosition();

    double getMovingDirection();

    boolean isDecelerate();

    boolean isAlive();

    KdTree<List<Move>> getPatternKdTree();
    KdTree<List<Move>> getSurferKdTree();
    List<Move> getMoveLog();
    double hit();

    void addFEnergy(double bulletDamage);
}
