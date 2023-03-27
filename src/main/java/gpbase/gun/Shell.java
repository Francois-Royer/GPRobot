package gpbase.gun;

import gpbase.Enemy;
import gpbase.MovingPoint;
import robocode.Rules;

public class Shell extends MovingPoint {
    private AimingData aimingData;
    public Shell(Double origin, AimingData aimingData, long start) {
        super(origin, Rules.getBulletSpeed(aimingData.getFirePower()), aimingData.getAngle(), start);
        this.aimingData = aimingData;
    }

    public Enemy getTarget(){
        return aimingData.getTarget();
    }
    public Gunner getGunner() {
        return aimingData.getGunner();
    }

    public AimingData getAimingData() { return aimingData; }

}
