package tankbase;

public class KDMove {
    private final double[] clusterKdPoint; // Point for kdtree
    Move move;
    private double[] antiSurferKdPoint; // Point for kdtree

    public KDMove(double[] clusterKdPoint, double[] antisurfer, double turn, double distance, long duration) {
        this.clusterKdPoint = clusterKdPoint;
        this.antiSurferKdPoint = antisurfer;
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
