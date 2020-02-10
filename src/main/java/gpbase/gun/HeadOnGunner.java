package gpbase.gun;

import gpbase.Enemy;
import gpbase.GPBase;
import robocode.Rules;

import static gpbase.GPUtils.*;
import static java.lang.Math.sqrt;

public class HeadOnGunner extends AbtractGunner {
    GPBase gpbase;

    public HeadOnGunner(GPBase gpbase) {
        this.gpbase = gpbase;
    }


    @Override
    public AimingData aim(Enemy enemy) {
        double confidence = getConfidence(enemy);
        double firePower = firePowerFromConfidenceAndEnergy(confidence, gpbase.getEnergy());
        return new AimingData(this, enemy, firePower, confidence);
    }

    public double getConfidence(Enemy enemy) {
        if (isEasyShot(enemy)) return 1;

        double distanceFactor = range(gpbase.getCurrentPoint().distance(enemy), GPBase.TANK_SIZE*4, gpbase.dmax, 1, .1);
        double velocityFactor = range(enemy.getVelocity(), 0, Rules.MAX_VELOCITY, 1, .1);

        return distanceFactor * velocityFactor;
    }

    public boolean isEasyShot(Enemy enemy) {
        return (enemy.getEnergy() == 0) || (enemy.getVelocity() == 0) || (enemy.distance(gpbase.getCurrentPoint()) < GPBase.TANK_SIZE*4);
    }
}