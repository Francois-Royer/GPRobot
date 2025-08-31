package tankbase.gun;

import tankbase.ITank;

import java.awt.*;

public interface Gun {
    String getName();

    Aiming aim(ITank target);

    double getFirePower(ITank target);

    FireStat getEnemyRoundFireStat(ITank target);

    void resetRoundStat();

    Color getColor();

    ITank getFirer();

    void setFirer(ITank firer);
}
