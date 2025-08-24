package tankbase.gun;

import tankbase.ITank;

import java.awt.*;

public interface Gunner {
    String getName();

    Aiming aim(ITank target);

    double getFirePower(ITank target);

    FireStat getEnemyRoundFireStat(ITank target);

    void resetRoundStat();

    Color getColor();

    ITank getGunner();

    void setGunner(ITank gunner);
}
