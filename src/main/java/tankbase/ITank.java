package tankbase;

import tankbase.gun.Shell;
import tankbase.gun.kdFormula.KDFormula;

import java.util.List;

public interface ITank {
    String getName();

    TankState getState();

    boolean isAlive();

    KDFormula getPatternFormula();

    KDFormula getSurferFormula();

    List<Move> getMoveLog();

    List<Shell> getFireLog(String targetName);

    void addFEnergy(double energy);

    double getFEnergy();

    long getLastStop();

    long getLastVelocityChange();

    long getLastChangeDirection();

    long getLastScan();
}
