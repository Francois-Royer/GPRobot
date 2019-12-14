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
        if (enemy.getEnergy() == 0) return 1;
        double distanceFactor = range(gpbase.getCurrentPoint().distance(enemy), gpbase.dmin, gpbase.dmax, -1, 1)*2;
        double confidence = range(enemy.getVelocity(), 0, Rules.MAX_VELOCITY, 1, 0);
        double dist2One = 1 - confidence;
        return checkMinMax(confidence - dist2One*distanceFactor, 0.05, 1);
    }
}