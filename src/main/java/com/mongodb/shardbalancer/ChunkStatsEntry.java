package com.mongodb.shardbalancer;

public class ChunkStatsEntry  implements Comparable<ChunkStatsEntry> {

	private String namespace;
	private String shard;
	private long totalOps;
	private int activeChunks;
	private double opsPerChunk;

	public ChunkStatsEntry(String ns, String shard, Long totalOps, Integer activeChunks) {
		this.namespace = ns;
		this.shard = shard;
		this.totalOps = totalOps;
		this.activeChunks = activeChunks;
		this.opsPerChunk = totalOps / (double)activeChunks;
	}
	
    @Override
    public int compareTo(ChunkStatsEntry other) {
        return Long.compare(other.totalOps, this.totalOps);
    }


	public String getNamespace() {
		return namespace;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public String getShard() {
		return shard;
	}

	public void setShard(String shard) {
		this.shard = shard;
	}

	public long getTotalOps() {
		return totalOps;
	}

	public void setTotalOps(long totalOps) {
		this.totalOps = totalOps;
	}

	public int getActiveChunks() {
		return activeChunks;
	}

	public void setActiveChunks(int activeChunks) {
		this.activeChunks = activeChunks;
	}

	public double getOpsPerChunk() {
		return opsPerChunk;
	}

	public void setOpsPerChunk(double opsPerChunk) {
		this.opsPerChunk = opsPerChunk;
	}

}