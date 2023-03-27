package gpbase.gun;

import gpbase.Enemy;

public class HeadOnGunner extends AbtractGunner {
    @Override
    public AimingData aim(Enemy enemy) {
        return new AimingData(this, enemy, getFirePower(enemy));
    }
}