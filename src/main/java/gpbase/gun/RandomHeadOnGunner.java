package gpbase.gun;

import gpbase.Enemy;
import gpbase.GPBase;

import java.awt.geom.Point2D;

public class RandomHeadOnGunner extends AbtractGunner {
    GPBase gpbase;

    public RandomHeadOnGunner(GPBase gpbase) {
        this.gpbase = gpbase;
    }


    @Override
    public AimingData aim(Enemy enemy) {
        AimingData ad = new AimingData(this, enemy, getFirePower(enemy));
        randomizeAngle(ad);
        return  ad;
    }

    private void randomizeAngle(AimingData ad) {
        double d = ad.getTarget().distance(gpbase.getCurrentPoint());
        Double maxRandom = Math.atan(GPBase.TANK_SIZE/2/d);
        Double var = (Math.floor(Math.random()*5)-2)/2;
        Double na = ad.getAngle()+ maxRandom * var;
        ad.setAngle(na);
        ad.setFiringPosition(new Point2D.Double(gpbase.getX()+d*Math.cos(na), gpbase.getY()+d*Math.sin(na)));
    }
}