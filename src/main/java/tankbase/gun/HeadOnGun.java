package tankbase.gun;

import tankbase.ITank;

public class HeadOnGun extends AbtractGun {
    public HeadOnGun(ITank tank) {
        super(tank);
    }

    @Override
    public Aiming aim(ITank target) {
        return new Aiming(this, target, getFirePower(target));
    }
}