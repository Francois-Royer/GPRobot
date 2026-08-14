package sample;

import static tankbase.TankUtils.getPointAngle;


public class %s extends

TankBase {

    @Override
    public void doGP () {
        firePower = avoidNan( % s, 0);
        turnGunLeft = getPointAngle(getState(), target.getState()) + avoidNan( % s, 0);
    }
}