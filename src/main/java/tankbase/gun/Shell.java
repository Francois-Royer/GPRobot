package tankbase.gun;

import robocode.Rules;
import tankbase.ITank;
import tankbase.MovingPoint;

public class Shell extends MovingPoint {
    private final AimingData aimingData;
    private final double dommage;

    public Shell(Double origin, AimingData aimingData, long start) {
        super(origin, Rules.getBulletSpeed(aimingData.getFirePower()), aimingData.getDirection(), start);
        this.aimingData = aimingData;
        this.dommage = Rules.getBulletDamage(aimingData.getFirePower());
    }

    public ITank getTarget() {
        return aimingData.getTarget();
    }

    public Gunner getGunner() {
        return aimingData.getGunner();
    }

    public AimingData getAimingData() {
        return aimingData;
    }
}
