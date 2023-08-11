package tankbase.gun;

import tankbase.ITank;

public class HeadOnGunner extends AbtractGunner {
    public HeadOnGunner(ITank tank) {
        super(tank);
    }

    @Override
    public AimingData aim(ITank target) {
        return new AimingData(this, target, getFirePower(target));
    }
}