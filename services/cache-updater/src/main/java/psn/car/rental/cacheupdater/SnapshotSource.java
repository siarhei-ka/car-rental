package psn.car.rental.cacheupdater;

/** Where a full snapshot of reference data comes from. */
public interface SnapshotSource {

  ReferenceCache.Snapshot load();
}
