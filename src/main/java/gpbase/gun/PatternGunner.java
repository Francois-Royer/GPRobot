package gpbase.gun;

import gpbase.Enemy;
import gpbase.GPBase;
import gpbase.Move;
import gpbase.dataStructures.trees.KD.NearestNeighborIterator;
import gpbase.dataStructures.trees.KD.SquareEuclideanDistanceFunction;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static gpbase.GPUtils.*;
import static java.lang.Math.*;
import static robocode.Rules.getBulletSpeed;

public class PatternGunner extends AbtractGunner {

    private static int PATTERN_SIZE = 7;
    GPBase gpbase;

    public PatternGunner(GPBase gpbase) {
        this.gpbase = gpbase;
    }


    @Override
    public AimingData aim(Enemy target) {
        return null;
    }
}
