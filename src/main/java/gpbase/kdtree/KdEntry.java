package gpbase.kdtree;

public class KdEntry<E> {
    private double coordinates[];
    private E data;
    private boolean deleted;


    public KdEntry(double[] coordinates, E data) {
        this.coordinates = coordinates;
        this.data = data;
        this.deleted = false;
    }

    public double[] getCoordinates() { return coordinates; }
    public void setCoordinates(double[] coordinates) { this.coordinates = coordinates; }

    public E getData() { return data; }
    public void setData(E data) { this.data = data; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
}
