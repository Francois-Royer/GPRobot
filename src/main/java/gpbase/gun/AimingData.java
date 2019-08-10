package gpbase.gun;

import gpbase.Enemy;

import java.awt.Point;
import java.util.List;

public class AimingData {
    Enemy target;
    Point.Double firingPosition;
    List<Point.Double> expectedMoves;
    double confidence;

}
