package tankbase.gun;

import tankbase.Enemy;
import tankbase.ITank;

import java.awt.*;

public interface Gunner {
    String getName();
    AimingData aim(ITank target);
    public double getFirePower(ITank target);

    FireStat getEnemyRoundFireStat(ITank target);
    void resetRoundStat();

    Color getColor();

    ITank getTank();
}
