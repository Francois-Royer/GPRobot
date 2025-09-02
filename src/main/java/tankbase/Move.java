package tankbase;

public class Move {
    private final double[] clusterKdPoint; // Point for kdtree
    private double[] antiSurferKdPoint; // Point for kdtree

    private final double turn;
    private final double distance;
    private final long duration;

    public Move(double[] clusterKdPoint, double[] antiSurferKdPoint, double turn, double distance, long duration) {
        this.clusterKdPoint = clusterKdPoint;
        this.antiSurferKdPoint = antiSurferKdPoint;
        this.turn = turn;
        this.distance = distance;
        this.duration = duration;
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

    public double getTurn() {
        return turn;
    }

    public double getDistance() {
        return distance;
    }

    public long getDuration() {
        return duration;
    }

    @Override
    public String toString() {
        return Double.toString(turn) + ',' + distance + ',' + duration + ',';
    }
}
