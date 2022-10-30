package gpbase.gun;

import gpbase.Enemy;
import gpbase.GPBase;

import java.awt.geom.Point2D;

public class OccilatorGunner extends AbtractGunner {
    GPBase gpbase;
    double step = 2*Math.PI/10;
    double o = 0;


    public OccilatorGunner(GPBase gpbase) {
        this.gpbase = gpbase;
    }


    @Override
    public AimingData aim(Enemy enemy) {
        AimingData ad = new AimingData(this, enemy, getFirePower(enemy));
        updateAngle(ad);
        return  ad;
    }

    void updateAngle(AimingData ad) {
        double d = ad.getTarget().distance(gpbase.getCurrentPoint());
        Double maxRandom = Math.atan(GPBase.TANK_SIZE/d);
        Double na = ad.getAngle()+ maxRandom * Math.sin(o);
        ad.setAngle(na);
        ad.setFiringPosition(new Point2D.Double(gpbase.getX()+d*Math.cos(na), gpbase.getY()+d*Math.sin(na)));
    }

    @Override
    public void fire(Enemy enemy) {
        super.fire(enemy);
        o += step;
        o%=2*Math.PI;
    }
}