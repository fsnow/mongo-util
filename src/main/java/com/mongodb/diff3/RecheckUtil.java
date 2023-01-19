package com.mongodb.diff3;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.pullAll;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.RawBsonDocument;
import org.bson.UuidRepresentation;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.model.Namespace;
import com.mongodb.shardsync.ChunkManager;
import com.mongodb.shardsync.ShardClient;
import com.mongodb.util.DiffUtils;

public class RecheckUtil {
	
	private static Logger logger = LoggerFactory.getLogger(RecheckUtil.class);

	private final ShardClient sourceShardClient;
	private final ShardClient destShardClient;
	
	private final MongoClient mongoClient;
	private final MongoDatabase db;
    private final MongoCollection<BsonDocument> coll;

	private final DiffConfiguration config;
	
	private ChunkManager chunkManager;
	private Bson chunkQuery;

	public RecheckUtil(DiffConfiguration config) {

        this.config = config;
        
        if (config.isFiltered()) {
        	chunkManager = new ChunkManager(config);
    		this.chunkQuery = chunkManager.initializeChunkQuery();
        }
        
        sourceShardClient = new ShardClient("source", config.getSourceClusterUri());
        destShardClient = new ShardClient("dest", config.getDestClusterUri());
        
        
        MongoClientSettings mongoClientSettings = MongoClientSettings.builder()
				.applyConnectionString(new ConnectionString(config.getStatusDbUri()))
				.uuidRepresentation(UuidRepresentation.STANDARD)
				.build();
		mongoClient = MongoClients.create(mongoClientSettings);
		
		this.db = this.mongoClient.getDatabase(config.getStatusDbName());
        this.coll = this.db.getCollection(config.getStatusDbCollName(), BsonDocument.class);


        sourceShardClient.init();
        destShardClient.init();
	}
	
	public void recheck() {
		
		FindIterable<BsonDocument> failedChunks = null;
		
		if (chunkQuery != null) {
			failedChunks = coll.find(chunkQuery);
		} else {
			failedChunks = coll.find();
		}
		
		
		for (BsonDocument failed : failedChunks) {
			
			Namespace ns = new Namespace(failed.getString("ns").getValue());
			MongoDatabase sourceDb = sourceShardClient.getMongoClient().getDatabase(ns.getDatabaseName());
			MongoCollection<RawBsonDocument> sourceColl = sourceDb.getCollection(ns.getCollectionName(), RawBsonDocument.class);
			
			MongoDatabase destDb = destShardClient.getMongoClient().getDatabase(ns.getDatabaseName());
			MongoCollection<RawBsonDocument> destColl = destDb.getCollection(ns.getCollectionName(), RawBsonDocument.class);
			
			recheck(failed, "mismatches", ns, sourceColl, destColl);
			recheck(failed, "srcOnly", ns, sourceColl, destColl);
			recheck(failed, "destOnly", ns, sourceColl, destColl);
			
		}
		
	}
	
	private void recheck(BsonDocument failed, String failedKey, Namespace ns, MongoCollection<RawBsonDocument> sourceColl, MongoCollection<RawBsonDocument> destColl) {
		
		BsonArray failures = failed.getArray(failedKey);
		
		List<BsonValue> passedKeys = new ArrayList<>();
		
		for (BsonValue m : failures) {
			
			BsonValue key = null;
			
			if (m instanceof BsonDocument) {
				BsonDocument d = (BsonDocument)m;
				
				if (d.containsKey("key")) {
					key = d.get("key");
				} else {
					key = d;
				}
			} else {
				key = m;
			}
				
			RawBsonDocument sourceDoc = null;
			RawBsonDocument destDoc = null;
			
			Iterator<RawBsonDocument> sourceDocs = sourceColl.find(eq("_id", key)).iterator();
			if (sourceDocs.hasNext()) {
				sourceDoc = sourceDocs.next();
			} else {
				logger.error("{}: source doc does not exist: {}", ns, key);
				continue;
			}
			if (sourceDocs.hasNext()) {
				logger.error("{}: duplicate source documents found with same key: {}", ns, key);
			}
			
			Iterator<RawBsonDocument> destDocs = destColl.find(eq("_id", key)).iterator();
			if (destDocs.hasNext()) {
				destDoc = destDocs.next();
			} else {
				logger.debug("{}: dest doc does not exist: {}", ns, key);
				continue;
			}
			if (destDocs.hasNext()) {
				logger.error("{}: duplicate dest documents found with same key: {}", ns, key);
			}
			
			boolean pass = compareDocuments(ns, sourceDoc, destDoc);
			
			if (pass) {
				passedKeys.add(m);
			}			
		}
		if (passedKeys.size() > 0) {
			Bson filter = eq("_id", failed.get("_id"));
			Bson update = pullAll(failedKey, passedKeys);
			UpdateResult result = coll.updateOne(filter, update);
			logger.debug("Status update result: {}", result);
		}

	}
	
	private boolean compareDocuments(Namespace ns, RawBsonDocument sourceDoc, RawBsonDocument destDoc) {
		byte[] sourceBytes = sourceDoc.getByteBuffer().array();
		byte[] destBytes = destDoc.getByteBuffer().array();
		
		Object id = sourceDoc.get("_id");
		
		if (sourceBytes.length == destBytes.length) {
			if (DiffUtils.compareHashes(sourceBytes, destBytes)) {
				//logger.debug("hashes match");
				return true;
			} else {
				if (sourceDoc.equals(destDoc)) {
					logger.warn(String.format("%s - docs equal, but hash mismatch, id: %s", ns, id));
					return true;
				} else {
					logger.error(String.format("%s - doc hash mismatch, id: %s", ns, id));
					return false;
				}
			}
		} else {
			
			boolean docCheck = DiffUtils.compareDocuments(ns.getNamespace(), sourceDoc, destDoc);
			if (docCheck) {
				logger.debug("{} - docs are equivalent, id: {}, sourceBytes: {}, destBytes: {}", ns, id, sourceBytes.length, destBytes.length);
				return true;
			} else {
				logger.debug("{} - docs are not equivalent, id: {}, sourceBytes: {}, destBytes: {}", ns, id, sourceBytes.length, destBytes.length);
				return false;
			}
		}
	}

}
