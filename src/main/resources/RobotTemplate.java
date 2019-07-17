package sampleex;

import robocode.*;
import static robocode.Rules.*;
import static robocode.util.Utils.*;

public class %s extends GPBase {

    @Override
    void doGP(){
        /*out.printf("doGP begin turnRadarLeft=%%.2f turnLeft=%%.2f ahead=%%.2f turnGunLeft=%%.2f fire=%%.2f\n",
            turnRadarLeft, turnLeft, ahead, turnGunLeft, fire);*/

        // --- PHENOME 1 ---
        turnRadarLeft=%s;

        // --- PHENOME 2 ---
        turnLeft=%s;

        // --- PHENOME 3 ---
        ahead=%s;

        // --- PHENOME 4 ---
        turnGunLeft=%s;

        // --- PHENOME 5 ---
        fire=%s;

        /*out.printf("doGP end turnRadarLeft=%%.2f turnLeft=%%.2f ahead=%%.2f turnGunLeft=%%.2f fire=%%.2f\n",
            turnRadarLeft, turnLeft, ahead, turnGunLeft, fire);*/
    }
}