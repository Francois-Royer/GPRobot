package tankbase;

public class KDMove {
    private final double[] clusterKdPoint; // Point for kdtree
    private double[] antiSurferKdPoint; // Point for kdtree

    Move move;

    public KDMove(double[] clusterKdPoint, double turn, double distance, long duration) {
        this.clusterKdPoint = clusterKdPoint;
        move = new Move(turn, distance, duration);
    }

    public double[] getClusterKdPoint() {
        return clusterKdPoint;
    }

    public double[] getAntiSurferKdPoint() {
        return antiSurferKdPoint;
    }

    public void setAntiSurferKdPoint(double[] antiSurferKdPoint) {
        this.antiSurferKdPoint = antiSurferKdPoint;
    }

    public Move getMove() {
        return move;
    }
}
