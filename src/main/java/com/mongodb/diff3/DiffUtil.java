package com.mongodb.diff3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.bson.Document;
import org.bson.RawBsonDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.model.DatabaseCatalog;
import com.mongodb.model.Namespace;
import com.mongodb.shardsync.ShardClient;
import com.mongodb.util.BlockWhenQueueFull;

public class DiffUtil {

    private static Logger logger = LoggerFactory.getLogger(DiffUtil.class);


    private ShardClient sourceShardClient;
    private ShardClient destShardClient;

    private DiffConfiguration config;

    protected ThreadPoolExecutor executor = null;
    private BlockingQueue<Runnable> workQueue;
    List<Future<DiffResult>> diffResults;

    private Map<String, RawBsonDocument> sourceChunksCache;
    private Set<String> chunkCollSet;
    private long estimatedTotalDocs;


    public DiffUtil(DiffConfiguration config) {
        this.config = config;

        sourceShardClient = new ShardClient("source", config.getSourceClusterUri());
        destShardClient = new ShardClient("dest", config.getDestClusterUri());

        sourceShardClient.init();
        destShardClient.init();
        sourceShardClient.populateCollectionsMap();
        sourceChunksCache = sourceShardClient.loadChunksCache(config.getChunkQuery());
        
        DatabaseCatalog catalog = sourceShardClient.getDatabaseCatalog();
        
        chunkCollSet = getShardedCollections();
        estimatedTotalDocs = estimateCount(chunkCollSet);
        
        // TODO - use this
        long est2 = catalog.getDocumentCount();

        workQueue = new ArrayBlockingQueue<Runnable>(sourceChunksCache.size());
        diffResults = new ArrayList<>(sourceChunksCache.size());
        executor = new ThreadPoolExecutor(config.getThreads(), config.getThreads(), 30, TimeUnit.SECONDS, workQueue, new BlockWhenQueueFull());
    }

    private Set<String> getShardedCollections() {
        Set<String> out = new HashSet<>();
        for (Document d : sourceShardClient.getCollectionsMap().values()) {
            String nsStr = (String) d.get("_id");
            out.add(nsStr);
        }
        return out;
    }

    private long estimateCount(Set<String> collSet) {
        long sum = 0l;
        for (String s : collSet) {
            Namespace ns = new Namespace(s);
            Number cnt = sourceShardClient.getFastCollectionCount(ns.getDatabaseName(), ns.getCollectionName());
            if (cnt != null) {
                sum += cnt.longValue();
            }
        }
        return sum;
    }

    public void run() {
        DiffSummary summary = new DiffSummary(sourceChunksCache.size(), estimatedTotalDocs);
        for (RawBsonDocument chunk : sourceChunksCache.values()) {
            DiffTask task = new DiffTask(sourceShardClient, destShardClient, config, chunk);
            diffResults.add(executor.submit(task));
        }
        
        // TODO iterate all non-sharded namespaces and create tasks for those also
        // Make DiffTask an abstract base class? ShardedDiffTask / UnShardedDiffTask?

        ScheduledExecutorService statusReporter = Executors.newSingleThreadScheduledExecutor();
        statusReporter.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                logger.info(summary.getSummary(false));
            }
        }, 0, 5, TimeUnit.SECONDS);


        executor.shutdown();

        for (Future<DiffResult> future : diffResults) {
            try {
                DiffResult result = future.get();
                int failures = result.getFailureCount();

                if (failures > 0) {
                    summary.incrementFailedChunks(1);
                    summary.incrementSuccessfulDocs(result.matches - failures);
                } else {
                    summary.incrementSuccessfulChunks(1);
                    summary.incrementSuccessfulDocs(result.matches);
                }


                summary.incrementProcessedDocs(result.matches + failures);
                summary.incrementFailedDocs(failures);
                summary.incrementProcessedChunks(1);
                summary.incrementSourceOnly(result.onlyOnSource);
                summary.incrementDestOnly(result.onlyOnDest);

                //logger.debug("result: {}", );
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (ExecutionException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        statusReporter.shutdown();
        logger.info(summary.getSummary(true));
    }


}
