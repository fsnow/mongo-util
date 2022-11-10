package com.mongodb.diff3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;

public class RetryTask implements Callable<DiffResult> {

    public static final RetryTask END_TOKEN = new RetryTask(null, null
            , null, null, null);
    private final RetryStatus retryStatus;
    private final AbstractDiffTask originalTask;
    private final DiffResult originalResult;
    private final List<String> failedIds;
    private final Queue<RetryTask> retryQueue;
    private static final Logger logger = LoggerFactory.getLogger(RetryTask.class);
    private long start;


    public RetryTask(RetryStatus retryStatus, AbstractDiffTask originalTask,
                     DiffResult originalResult, List<String> failedIds, Queue<RetryTask> retryQueue) {
        this.retryStatus = retryStatus;
        this.originalTask = originalTask;
        this.originalResult = originalResult;
        this.failedIds = failedIds;
        this.retryQueue = retryQueue;
    }

    @Override
    public DiffResult call() throws Exception {
        logger.debug("[{}] got a retry task", Thread.currentThread().getName());
        start = System.currentTimeMillis();
        DiffResult result;// = newDiffResult();

        if (retryStatus.canRetry()) {
            try {
                originalTask.computeDiff(failedIds);
            } catch (Exception e) {
                logger.error("Fatal error performing diffs, ns: {}",
                        originalTask.namespace.getNamespace());
                throw new RuntimeException(e);
            } finally {
                originalTask.closeCursor(originalTask.sourceCursor);
                originalTask.closeCursor(originalTask.destCursor);
            }
            result = originalResult.mergeRetryResult(originalTask.result);

            if (result.getFailureCount() > 0) {
                RetryStatus newRetryStatus = retryStatus.increment();
                if (newRetryStatus != null) {
                    RetryTask newRetryTask = new RetryTask(
                            newRetryStatus, originalTask, result, result.failedIds, retryQueue);
                    logger.debug("[{}] retry for ({}-{}) failed ({} ids); submitting attempt {} to retryQueue",
                            Thread.currentThread().getName(), originalTask.namespace.getNamespace(),
                            originalTask.chunkString, result.getFailureCount(), newRetryStatus.getAttempt());
                    retryQueue.add(newRetryTask);
                } else {
                    logger.info("[{}] retry task for ({}-{}) failed; too many retry attempts",
                            Thread.currentThread().getName(), originalTask.namespace.getNamespace(),
                            originalTask.chunkString);
                    retryQueue.add(END_TOKEN);
                }
            }
        } else {
            result = originalResult;
            result.retryable = false;
        }

        logger.debug("[{}] completed a retry task in {} ms :: {}",
                Thread.currentThread().getName(), System.currentTimeMillis() - start, result);

        return result;
    }

    // TODO - come up with something better than this jive..
    private DiffResult newDiffResult() {
        if (originalResult instanceof ShardedDiffResult) {
            ShardedDiffResult sdr = new ShardedDiffResult();
            sdr.ns = originalResult.ns;
            return sdr;
        } else if (originalResult instanceof UnshardedDiffResult) {
            return new UnshardedDiffResult(originalResult.ns);
        } else {
            throw new RuntimeException("Unexpected diff result class: " + originalResult.getClass());
        }
    }

    public AbstractDiffTask getOriginalTask() {
        return originalTask;
    }

    public RetryStatus getRetryStatus() {
        return retryStatus;
    }
}
