package tankbase.gun;

import tankbase.ITank;

public class HeadOnGunner extends AbtractGunner {
    public HeadOnGunner(ITank tank) {
        super(tank);
    }

    @Override
    public Aiming aim(ITank target) {
        return new Aiming(this, target, getFirePower(target));
    }
}