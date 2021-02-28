package gpbase.gun;

import gpbase.Enemy;
import gpbase.GPBase;

import static gpbase.GPUtils.*;
import static robocode.Rules.*;

public class HeadOnGunner extends AbtractGunner {
    GPBase gpbase;

    public HeadOnGunner(GPBase gpbase) {
        this.gpbase = gpbase;
    }


    @Override
    public AimingData aim(Enemy enemy) {
        return new AimingData(this, enemy, getFirePower(enemy));
    }
}