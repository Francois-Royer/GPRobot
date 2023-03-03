package sample;

import static gpbase.GPUtils.*;
import static java.lang.Math.*;
import static robocode.util.Utils.*;
import static robocode.Rules.*;

import robocode.Rules;
import gpbase.GPBase;

public class %s extends GPBase {

    @Override
    public void doGP() {
        // --- PHENOME 1 ---
        turnGunLeft=AvoidNan(%s, 0);

        // --- PHENOME 2 ---
        fire=AvoidNan(%s, 0);

        // --- PHENOME 3 ---
        //turnLeft=AvoidNan(#s, 0);

        // --- PHENOME 4 ---
        //ahead=AvoidNan(#s, 0);
    }
}