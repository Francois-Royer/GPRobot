package tankbase;

import tankbase.gun.kdformula.KDFormula;

import java.util.List;

public interface ITank {
    String getName();

    TankState getState();

    boolean isAlive();

    KDFormula getPatternFormula();

    KDFormula getSurferFormula();

    List<Move> getMoveLog();

    void addFEnergy(double energy);

    double getFEnergy();

    long getLastStop();

    long getLastVelocityChange();

    long getLastChangeDirection();

    long getLastScan();
}
