package tankbase.kdtree.dataStructures.trees.thirdGenKD;

public interface DistanceFunction {
    double distance(double[] p1, double[] p2);

    double distanceToRect(double[] point, double[] min, double[] max);
}

