package io.split.engine.experiments;

/**
 * Created by adilaijaz on 5/8/15.
 */
public interface SplitFetcher extends Runnable {
    /**
     * Forces a sync of splits, outside of any scheduled
     * syncs. This method MUST NOT throw any exceptions.
     */
    void forceRefresh(boolean addCacheHeader);

    /**
     * Forces a sync of ALL splits, outside of any scheduled
     * syncs. This method MUST NOT throw any exceptions.
     */
    void fetchAll(boolean addCacheHeader);
}
