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
        //turnLeft=AvoidNan(#s, 0);

        // --- PHENOME 2 ---
        turnGunLeft=AvoidNan(%s, 0);

        // --- PHENOME 3 ---
        //ahead=AvoidNan(#s, 0);

        // --- PHENOME 4 ---
        //fire=AvoidNan(#s, 0);
    }
}