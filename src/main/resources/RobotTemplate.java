package sample;

import tankbase.TankBase;
import java.lang.Math;
import static java.lang.Math.*;
import static tankbase.Constant.*;
import static tankbase.TankUtils.*;
import static robocode.Rules.*;


public class %s extends TankBase {

    @Override
    public void doGP () {
        firePower = avoidNan( %s, 0);
        turnGunLeft = getPointAngle(getState(), target.getState()) + avoidNan( %s, 0);
    }
}