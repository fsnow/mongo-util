package com.mongodb.shardsync;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShardConfigSyncApp {
    
    private static Logger logger = LoggerFactory.getLogger(ShardConfigSyncApp.class);
    
    private static Options options;
    private static CommandLine line;
    
    private final static String SOURCE_URI = "source";
    private final static String DEST_URI = "dest";
    
    private final static String SOURCE_URI_PATTERN = "sourceUriPattern";
    private final static String DEST_URI_PATTERN = "destUriPattern";
    private final static String DEST_CSRS_URI = "destCsrs";
    
    private final static String SOURCE_RS_PATTERN = "sourceRsPattern";
    private final static String DEST_RS_PATTERN = "destRsPattern";
    
    // Advanced option for manaually overriding the hostnames / bypassing discovery
    private final static String SOURCE_RS_MANUAL = "sourceRsManual";
    private final static String DEST_RS_MANUAL = "destRsManual";
    
    private final static String MONGOMIRROR_BINARY = "mongomirrorBinary";

    private final static String DROP_DEST_DBS = "dropDestDbs";
    private final static String DROP_DEST_DBS_AND_CONFIG_METADATA = "dropDestDbsAndConfigMeta";
    public final static String NON_PRIVILEGED = "nonPrivileged";
    private final static String PRESERVE_UUIDS = "preserveUUIDs";
    private final static String NO_INDEX_RESTORE = "noIndexRestore";
    private final static String COMPRESSORS = "compressors";
    private final static String COLL_STATS_THRESHOLD = "collStatsThreshold";
    
    private final static String COLL_COUNTS = "compareCounts";
    private final static String CHUNK_COUNTS = "chunkCounts";
    private final static String FLUSH_ROUTER = "flushRouter";
    private final static String SYNC_METADATA = "syncMetadata";
    private final static String SYNC_METADATA_OPTIMIZED = "syncMetadataOptimized";
    private final static String SYNC_USERS = "syncUsers";
    private final static String COMPARE_CHUNKS = "compareChunks";
    private final static String COMPARE_COLLECTION_UUIDS = "compareCollectionUuids";
    private final static String DISABLE_SOURCE_AUTOSPLIT = "disableSourceAutosplit";
    private final static String MONGOMIRROR_START_PORT = "mongoMirrorStartPort";
    private final static String OPLOG_BASE_PATH = "oplogBasePath";
    private final static String BOOKMARK_FILE_PREFIX = "bookmarkFilePrefix";
    private final static String SKIP_FLUSH_ROUTER_CONFIG = "skipFlushRouterConfig";
    
    private final static String COMPARE_AND_MOVE_CHUNKS = "compareAndMoveChunks";
    private final static String MONGO_MIRROR = "mongomirror";
    private final static String DRY_RUN = "dryRun";
    private final static String TAIL_FROM_TS = "tailFromTs";
    private final static String TAIL_FROM_NOW = "tailFromNow";
    private final static String TAIL_FROM_LATEST_OPLOG_TS = "tailFromLatestOplogTs";
    private final static String SHARD_COLLECTIONS = "shardCollections";
    private final static String CLEANUP_ORPHANS = "cleanupOrphans";
    private final static String CLEANUP_ORPHANS_SLEEP = "cleanupOrphansSleep";
    private final static String CLEANUP_ORPHANS_DEST = "cleanupOrphansDest";
    private final static String SYNC_INDEXES = "syncIndexes";
    private final static String COMPARE_INDEXES = "compareIndexes";
    private final static String EXTEND_TTL = "extendTtl";
    private final static String CLEANUP_PREVIOUS_ALL = "cleanupPreviousAll";
    private final static String CLEANUP_PREVIOUS_SHARDS = "cleanupPreviousShards";
    
    private final static String SSL_ALLOW_INVALID_HOSTNAMES = "sslAllowInvalidHostnames";
    private final static String SSL_ALLOW_INVALID_CERTS = "sslAllowInvalidCertificates";

    private static final String SHARD_MAP = "shardMap";
    
    private static final String ATLAS_API_PUBLIC_KEY = "atlasApiPublicKey";
    private static final String ATLAS_API_PRIVATE_KEY = "atlasApiPrivateKey";
    private static final String ATLAS_PROJECT_ID = "atlasProjectId";
    
    
    @SuppressWarnings("static-access")
    private static CommandLine initializeAndParseCommandLineOptions(String[] args) {
        options = new Options();
        options.addOption(new Option("help", "print this message"));
        options.addOption(OptionBuilder.withArgName("Configuration properties file").hasArg().withLongOpt("config")
                .create("c"));
        options.addOption(OptionBuilder.withArgName("Source cluster connection uri").hasArg().withLongOpt(SOURCE_URI)
                .create("s"));
        options.addOption(OptionBuilder.withArgName("Destination cluster connection uri").hasArg().withLongOpt(DEST_URI)
                .create("d"));
        
        options.addOption(OptionBuilder.withArgName("Drop destination databases, but preserve config metadata")
                .withLongOpt(DROP_DEST_DBS).create(DROP_DEST_DBS));
        options.addOption(OptionBuilder.withArgName("Drop destination databases AND config metadata (collections, databases, chunks)")
                .withLongOpt(DROP_DEST_DBS_AND_CONFIG_METADATA).create(DROP_DEST_DBS_AND_CONFIG_METADATA));
        options.addOption(OptionBuilder.withArgName("Non-privileged mode, create chunks using splitChunk")
                .withLongOpt(NON_PRIVILEGED).create(NON_PRIVILEGED));
        options.addOption(OptionBuilder.withArgName("Compare counts only (do not sync/migrate)")
                .withLongOpt(COLL_COUNTS).create(COLL_COUNTS));
        options.addOption(OptionBuilder.withArgName("Show chunk counts when collection counts differ")
                .withLongOpt(CHUNK_COUNTS).create(CHUNK_COUNTS));
        options.addOption(OptionBuilder.withArgName("Flush router config on all mongos (do not sync/migrate)")
                .withLongOpt(FLUSH_ROUTER).create(FLUSH_ROUTER));
        options.addOption(OptionBuilder.withArgName("Compare all shard chunks (do not sync/migrate)")
                .withLongOpt(COMPARE_CHUNKS).create(COMPARE_CHUNKS));
        options.addOption(OptionBuilder.withArgName("Compare all shard chunks, move any misplaced chunks")
                .withLongOpt(COMPARE_AND_MOVE_CHUNKS).create(COMPARE_AND_MOVE_CHUNKS));
        options.addOption(OptionBuilder.withArgName("Compare all collection UUIDs")
                .withLongOpt(COMPARE_COLLECTION_UUIDS).create(COMPARE_COLLECTION_UUIDS));
        options.addOption(OptionBuilder.withArgName("Cleanup (remove) destination data on all shards")
                .withLongOpt(CLEANUP_PREVIOUS_ALL).create(CLEANUP_PREVIOUS_ALL));
        options.addOption(OptionBuilder.withArgName("Cleanup (remove) destination data on specified shards").hasArgs()
                .withLongOpt(CLEANUP_PREVIOUS_SHARDS).create(CLEANUP_PREVIOUS_SHARDS));
        
        options.addOption(OptionBuilder.withArgName("Synchronize shard metadata")
                .withLongOpt(SYNC_METADATA).create(SYNC_METADATA));
        options.addOption(OptionBuilder.withArgName("Synchronize shard metadata (optimized method)")
                .withLongOpt(SYNC_METADATA_OPTIMIZED).create(SYNC_METADATA_OPTIMIZED));
        options.addOption(OptionBuilder.withArgName("Synchronize users and roles")
                .withLongOpt(SYNC_USERS).create(SYNC_USERS));
        options.addOption(OptionBuilder.withArgName("Shard mapping").hasArg().withLongOpt(SHARD_MAP)
                .isRequired(false).create("m"));
        options.addOption(OptionBuilder.withArgName("Disable autosplit on source cluster")
                .withLongOpt(DISABLE_SOURCE_AUTOSPLIT).create());
        options.addOption(OptionBuilder.withArgName("Cleanup source orphans")
                .withLongOpt(CLEANUP_ORPHANS).create(CLEANUP_ORPHANS));
        options.addOption(OptionBuilder.withArgName("Cleanup destination orphans")
                .withLongOpt(CLEANUP_ORPHANS_DEST).create(CLEANUP_ORPHANS_DEST));
        options.addOption(OptionBuilder.withArgName("cleanup orphans sleep millis").hasArg()
                .withLongOpt(CLEANUP_ORPHANS_SLEEP).create(CLEANUP_ORPHANS_SLEEP));
        
        options.addOption(OptionBuilder.withArgName("Shard destination collections")
                .withLongOpt(SHARD_COLLECTIONS).create());
        options.addOption(OptionBuilder.withArgName("Copy indexes from source to dest")
                .withLongOpt(SYNC_INDEXES).create(SYNC_INDEXES));
        options.addOption(OptionBuilder.withArgName("Compare indexes from source to dest")
                .withLongOpt(COMPARE_INDEXES).create(COMPARE_INDEXES));
        options.addOption(OptionBuilder.withArgName("Extend TTL expiration (use with syncIndexes)")
                .withLongOpt(EXTEND_TTL).create(EXTEND_TTL));
        options.addOption(OptionBuilder.withArgName("Skip the flushRouterConfig step")
                .withLongOpt(SKIP_FLUSH_ROUTER_CONFIG).create(SKIP_FLUSH_ROUTER_CONFIG));
        
        options.addOption(OptionBuilder.withArgName("ssl allow invalid hostnames")
                .withLongOpt(SSL_ALLOW_INVALID_HOSTNAMES).create(SSL_ALLOW_INVALID_HOSTNAMES));
        options.addOption(OptionBuilder.withArgName("ssl allow invalid certificates")
                .withLongOpt(SSL_ALLOW_INVALID_CERTS).create(SSL_ALLOW_INVALID_CERTS));
        
        
        // Mongomirror options
        options.addOption(OptionBuilder.withArgName("Execute mongomirror(s)")
                .withLongOpt(MONGO_MIRROR).create(MONGO_MIRROR));
        options.addOption(OptionBuilder.withArgName("Dry run only")
                .withLongOpt(DRY_RUN).create(DRY_RUN));
        options.addOption(OptionBuilder.withArgName("mongomirror tail only starting at current ts")
                .withLongOpt(TAIL_FROM_NOW).create(TAIL_FROM_NOW));
        options.addOption(OptionBuilder.withArgName("mongomirror tail only starting from latest oplog ts")
                .withLongOpt(TAIL_FROM_LATEST_OPLOG_TS).create(TAIL_FROM_LATEST_OPLOG_TS));
        options.addOption(OptionBuilder.withArgName("mongomirror tail only from specificed oplog ts (ts,increment format)")
                .withLongOpt(TAIL_FROM_TS).hasArg().create(TAIL_FROM_TS));
        options.addOption(OptionBuilder.withArgName("mongomirror namespace filter").hasArgs()
        		.withLongOpt("filter").create("f"));
        options.addOption(OptionBuilder.withArgName("full path to mongomirror binary").hasArg()
        		.withLongOpt(MONGOMIRROR_BINARY).create("p"));
        options.addOption(OptionBuilder.withArgName("mongomirror preserve dest UUIDs (not supported for Atlas dest)")
                .withLongOpt(PRESERVE_UUIDS).create(PRESERVE_UUIDS));
        options.addOption(OptionBuilder.withArgName("skip build indexes")
                .withLongOpt(NO_INDEX_RESTORE).create(NO_INDEX_RESTORE));
        options.addOption(OptionBuilder.withArgName("mongomirror compressors").hasArg()
                .withLongOpt(COMPRESSORS).create(COMPRESSORS));
        options.addOption(OptionBuilder.withArgName("mongomirror collStatsThreshold").hasArg()
                .withLongOpt(COLL_STATS_THRESHOLD).create(COLL_STATS_THRESHOLD));
        options.addOption(OptionBuilder.withArgName("mongomirror http status starting port (default 9001)").hasArg()
                .withLongOpt(MONGOMIRROR_START_PORT).create());
        options.addOption(OptionBuilder.withArgName("mongomirror numParallelCollections").hasArg()
        		.withLongOpt("numParallelCollections").create("y"));
        options.addOption(OptionBuilder.withArgName("mongomirror writeConcern").hasArg().withLongOpt("writeConcern")
                .isRequired(false).create("w"));
        options.addOption(OptionBuilder.withArgName("mongomirror oplogPath base path").hasArg()
                .withLongOpt(OPLOG_BASE_PATH).create(OPLOG_BASE_PATH));
        options.addOption(OptionBuilder.withArgName("mongomirror bookmark filename prefix").hasArg()
                .withLongOpt(BOOKMARK_FILE_PREFIX).create());
        
        
        options.addOption(OptionBuilder.withArgName("Sleep millis").hasArg().withLongOpt("sleepMillis")
                .isRequired(false).create("x"));
        options.addOption(OptionBuilder.withArgName("Diff chunks").hasArgs().withLongOpt("diffChunks")
                .isRequired(false).create("z"));
        
        options.addOption(OptionBuilder.withArgName("shardToRs").withLongOpt("shardToRs")
                .isRequired(false).create("r"));
        
        options.addOption(OptionBuilder.withArgName("diffShardKeys [sync|diff]").withLongOpt("diffShardKeys")
                .isRequired(false).hasArg().create("k"));
        
        

        CommandLineParser parser = new GnuParser();
        try {
            line = parser.parse(options, args);
            if (line.hasOption("help")) {
                printHelpAndExit();
            }
        } catch (org.apache.commons.cli.ParseException e) {
            System.out.println(e.getMessage());
            printHelpAndExit();
        } catch (Exception e) {
            e.printStackTrace();
            printHelpAndExit();
        }

        return line;
    }

    private static void printHelpAndExit() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("shardSync", options);
        System.exit(-1);
    }
    
    private static Configuration readProperties() {
    	Configurations configs = new Configurations();
    	Configuration defaultConfig = new PropertiesConfiguration();
    	
        File propsFile = null;
        if (line.hasOption("c")) {
            propsFile = new File(line.getOptionValue("c"));
        } else {
            propsFile = new File("shard-sync.properties");
            if (! propsFile.exists()) {
                logger.warn("Default config file shard-sync.properties not found, using command line options only");
                return defaultConfig;
            }
        }
        
        try {
			Configuration config = configs.properties(propsFile);
			return config;
		} catch (ConfigurationException e) {
			logger.error("Error loading properties file: " + propsFile, e);
		}
        return defaultConfig;
    }

    public static void main(String[] args) throws Exception {
        CommandLine line = initializeAndParseCommandLineOptions(args);
        
        
        Configuration properties = readProperties();
        
        SyncConfiguration config = new SyncConfiguration();
        config.setSourceClusterUri(line.getOptionValue("s", properties.getString(SOURCE_URI)));
        config.setDestClusterUri(line.getOptionValue("d", properties.getString(DEST_URI)));
        
        config.setAtlasApiPublicKey(properties.getString(ATLAS_API_PUBLIC_KEY));
        config.setAtlasApiPrivateKey(properties.getString(ATLAS_API_PRIVATE_KEY));
        config.setAtlasProjectId(properties.getString(ATLAS_PROJECT_ID));
        
        config.setSourceClusterPattern(properties.getString(SOURCE_URI_PATTERN));
        config.setDestClusterPattern(properties.getString(DEST_URI_PATTERN));
        
        config.setSourceRsManual(properties.getStringArray(SOURCE_RS_MANUAL));
        config.setDestRsManual(properties.getStringArray(DEST_RS_MANUAL));
        
        
        config.setDestCsrsUri(properties.getString(DEST_CSRS_URI));
        
        config.setSourceRsPattern(properties.getString(SOURCE_RS_PATTERN));
        config.setDestRsPattern(properties.getString(DEST_RS_PATTERN));
        
        if ((config.getSourceClusterUri() == null && config.getSourceClusterPattern() == null) 
        	|| (config.getDestClusterUri() == null && config.getDestClusterPattern() == null)) {
            System.out.println(String.format("%s/%s and/or %s/%s options required", 
            		SOURCE_URI, DEST_URI, SOURCE_URI_PATTERN, DEST_URI_PATTERN));
            printHelpAndExit();
        }
        
        String shardMaps = properties.getString(SHARD_MAP);
        if (shardMaps != null) {
        	config.setShardMap(shardMaps.split(","));
        } else {
        	config.setShardMap(line.getOptionValues("m"));
        }
        
        config.setNamespaceFilters(line.getOptionValues("f"));
        
        boolean nonPrivilegedMode = line.hasOption(NON_PRIVILEGED) || properties.getBoolean(NON_PRIVILEGED, false);
        config.setNonPrivilegedMode(nonPrivilegedMode);
        boolean noIndexRestore = line.hasOption(NO_INDEX_RESTORE) || properties.getBoolean(NON_PRIVILEGED, false);
        config.setNoIndexRestore(noIndexRestore);
        config.setDropDestDbs(line.hasOption(DROP_DEST_DBS));
        config.setDropDestDbsAndConfigMetadata(line.hasOption(DROP_DEST_DBS_AND_CONFIG_METADATA));
        config.setSleepMillis(line.getOptionValue("x"));
        config.setNumParallelCollections(line.getOptionValue("y"));
        config.setWriteConcern(line.getOptionValue("w"));
        config.setDryRun(line.hasOption(DRY_RUN));
        
        config.setSslAllowInvalidCertificates(line.hasOption(SSL_ALLOW_INVALID_CERTS));
        config.setSslAllowInvalidHostnames(line.hasOption(SSL_ALLOW_INVALID_HOSTNAMES));
        
        if (line.hasOption(SKIP_FLUSH_ROUTER_CONFIG) || properties.getBoolean(SKIP_FLUSH_ROUTER_CONFIG, false)) {
        	config.setSkipFlushRouterConfig(true);
        }
        
        if (line.hasOption("r")) {
        	config.setShardToRs(true);
        }
        
        ShardConfigSync sync = new ShardConfigSync(config);
        sync.initialize();
        
        boolean actionFound = false;
        if (line.hasOption(COLL_COUNTS)) {
            actionFound = true;
            sync.compareShardCounts();
        } else if (line.hasOption(CHUNK_COUNTS)) {
        	actionFound = true;
        	sync.compareChunkCounts();
        } else if (line.hasOption(FLUSH_ROUTER)) {
            actionFound = true;
            sync.flushRouterConfig();
        } else if (line.hasOption(DISABLE_SOURCE_AUTOSPLIT)) {
            actionFound = true;
            sync.disableSourceAutosplit();
        } else if (line.hasOption(COMPARE_CHUNKS)) {
            actionFound = true;
            sync.compareChunks();
        } else if (line.hasOption(COMPARE_AND_MOVE_CHUNKS)) {
            actionFound = true;
            if (nonPrivilegedMode) {
            	sync.compareAndMoveChunks(true, false);
            } else {
            	//sync.compareAndMovePrivileged();
            	sync.compareAndMoveChunks(true, false);
            }
            
        } else if (line.hasOption(COMPARE_COLLECTION_UUIDS)) {
        	actionFound = true;
        	sync.compareCollectionUuids();
        } else if (line.hasOption(SYNC_METADATA)) {
            actionFound = true;
            sync.syncMetadata();
        } else if (line.hasOption(SYNC_METADATA_OPTIMIZED)) {
            actionFound = true;
            sync.syncMetadataOptimized();
        } else if (line.hasOption(SYNC_USERS)) {
            actionFound = true;
            sync.syncUsers();
        }  else if (line.hasOption(SHARD_COLLECTIONS)) {
            actionFound = true;
            sync.shardCollections();
        } else if (line.hasOption(SYNC_INDEXES)) {
            actionFound = true;
            boolean extendTtl = line.hasOption(EXTEND_TTL);
            sync.syncIndexesShards(true, extendTtl);
        } else if (line.hasOption(COMPARE_INDEXES)) {
            actionFound = true;
            boolean extendTtl = line.hasOption(EXTEND_TTL);
            sync.syncIndexesShards(false, extendTtl);
        } else if (line.hasOption("k")) {
            String opt = line.getOptionValue("k");
            boolean doSync = opt.equals("sync");
            actionFound = true;
            sync.diffShardedCollections(doSync);
        }  else if (line.hasOption(CLEANUP_ORPHANS)) {
            actionFound = true;
            config.setCleanupOrphansSleepMillis(line.getOptionValue(CLEANUP_ORPHANS_SLEEP));
            sync.cleanupOrphans();
        }  else if (line.hasOption(CLEANUP_ORPHANS_DEST)) {
            actionFound = true;
            sync.cleanupOrphansDest();
        } else if (line.hasOption(DROP_DEST_DBS)) {
            actionFound = true;
            sync.dropDestinationDatabases();
        } else if (line.hasOption(DROP_DEST_DBS_AND_CONFIG_METADATA)) {
            actionFound = true;
            sync.dropDestinationDatabasesAndConfigMetadata();
        } else if (line.hasOption(CLEANUP_PREVIOUS_ALL)) {
        	actionFound = true;
        	sync.cleanupPreviousAll();
        } else if (line.hasOption(CLEANUP_PREVIOUS_SHARDS)) {
        	actionFound = true;
        	String[] shardNames = line.getOptionValues(CLEANUP_PREVIOUS_SHARDS);
        	Set<String> shardNamesSet = new HashSet<>();
        	shardNamesSet.addAll(Arrays.asList(shardNames));
        	sync.cleanupPreviousShards(shardNamesSet);
        }
        
        
        // MONGOMIRROR_BINARY
        if (line.hasOption(MONGO_MIRROR) && ! line.hasOption("r")) {
            actionFound = true;
            String mongoMirrorPath = line.getOptionValue("p", properties.getString(MONGOMIRROR_BINARY));
            boolean preserveUUIDs = line.hasOption(PRESERVE_UUIDS);
            boolean extendTtl = line.hasOption(EXTEND_TTL);
            
            if (line.hasOption(COLL_STATS_THRESHOLD)) {
            	Integer collStatsThreshold = Integer.parseInt(line.getOptionValue(COLL_STATS_THRESHOLD));
            	config.setCollStatsThreshold(collStatsThreshold);
            } else if (properties.getProperty(COLL_STATS_THRESHOLD) != null) {
            	config.setCollStatsThreshold(properties.getInt(COLL_STATS_THRESHOLD));
            }
            
            if (line.hasOption(COMPRESSORS)) {
            	config.setCompressors(line.getOptionValue(COMPRESSORS));
            }
            if (line.hasOption(MONGOMIRROR_START_PORT)) {
            	Integer startPort = Integer.parseInt(line.getOptionValue(MONGOMIRROR_START_PORT));
            	config.setMongoMirrorStartPort(startPort);
            }
            config.setOplogBasePath(line.getOptionValue(OPLOG_BASE_PATH));
            config.setBookmarkFilePrefix(line.getOptionValue(BOOKMARK_FILE_PREFIX));
            
            if (mongoMirrorPath == null) {
                System.out.println("mongomirrorPath required");
                printHelpAndExit();
            }
            config.setMongomirrorBinary(mongoMirrorPath);
            config.setDropDestDbs(line.hasOption(DROP_DEST_DBS));
            config.setPreserveUUIDs(preserveUUIDs);
            config.setNoIndexRestore(noIndexRestore);
            config.setExtendTtl(extendTtl);
            
            if (line.hasOption(TAIL_FROM_NOW)) {
            	sync.mongomirrorTailFromNow();
            } else if (line.hasOption(TAIL_FROM_LATEST_OPLOG_TS)) {
            	sync.mongomirrorTailFromLatestOplogTs(null);
            } else if (line.hasOption(TAIL_FROM_TS)) {
            	sync.mongomirrorTailFromTs(line.getOptionValue(TAIL_FROM_TS));
            } else {
            	sync.mongomirror();
            }
        }
        
        if (line.hasOption("r") && line.hasOption(MONGO_MIRROR)) {
        	logger.debug("shardToRs");
            actionFound = true;
            String mongoMirrorPath = line.getOptionValue("p", properties.getString(MONGOMIRROR_BINARY));
            config.setMongomirrorBinary(mongoMirrorPath);
            config.setDropDestDbs(line.hasOption(DROP_DEST_DBS));
            sync.shardToRs();
        }
        
        if (! actionFound) {
            System.out.println("Missing action");
            printHelpAndExit();
        }
        
        // String[] fileNames = line.getOptionValues("f");
        // client.setEndpointUrl(line.getOptionValue("u"));

    }

}
