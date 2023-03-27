package gpbase.gun;

import gpbase.Enemy;
import gpbase.GPBase;

import java.awt.*;
import java.awt.geom.Point2D;

public class CircularOccilatorGunner extends CircularGunner {

    @Override
    public AimingData aim(Enemy enemy) {
        AimingData ad = super.aim(enemy);
        addOscilation(ad);
        return  ad;
    }

    @Override
    public Color getColor() { return Color.ORANGE; }

    void addOscilation(AimingData ad) {
        double d = ad.getFiringPosition().distance(ad.getTarget().getGpBase().getCurrentPoint());
        Double arc = Math.atan(GPBase.TANK_SIZE/2/d);
        Double na = ad.getAngle()+ arc * Math.sin(ad.getTarget().getGpBase().now);
        ad.setAngle(na);
        ad.setFiringPosition(new Point2D.Double(ad.getTarget().getGpBase().getX()+d*Math.cos(na),
                ad.getTarget().getGpBase().getY()+d*Math.sin(na)));
    }
}