package tankbase;

import tankbase.gun.Shell;
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

    double getGunHeat();

    boolean isDecelerate();

    boolean isAlive();

    KdTree<List<Move>> getPatternKdTree();

    KdTree<List<Move>> getSurferKdTree();

    List<Move> getMoveLog();

    List<Shell> getFireLog(String targetName);

    double hit();

    void addFEnergy(double energy);

    double getFEnergy();

    int getAliveCount();

    long getDate();

    long getLastStop();

    long getLastChangeDirection();

    long getLastScan();
}
