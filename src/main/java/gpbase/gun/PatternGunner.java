package gpbase.gun;

import gpbase.Enemy;
import gpbase.GPBase;

public class PatternGunner extends AbtractGunner {

    private static int PATTERN_SIZE = 7;
    GPBase gpbase;

    public PatternGunner(GPBase gpbase) {
        this.gpbase = gpbase;
    }


    @Override
    public AimingData aim(Enemy target) {
        return null;
    }
}
