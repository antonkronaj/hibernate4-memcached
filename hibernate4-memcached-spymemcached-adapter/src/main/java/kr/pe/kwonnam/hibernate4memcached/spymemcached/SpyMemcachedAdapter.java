package kr.pe.kwonnam.hibernate4memcached.spymemcached;

import kr.pe.kwonnam.hibernate4memcached.Hibernate4MemcachedRegionFactory;
import kr.pe.kwonnam.hibernate4memcached.memcached.CacheNamespace;
import kr.pe.kwonnam.hibernate4memcached.memcached.MemcachedAdapter;
import kr.pe.kwonnam.hibernate4memcached.util.OverridableReadOnlyProperties;
import net.spy.memcached.*;
import net.spy.memcached.transcoders.Transcoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Constructor;

/**
 * SpymemcachedAdapter for hibernate4memcached.
 *
 * @author KwonNam Son (kwon37xi@gmail.com)
 */
public class SpyMemcachedAdapter implements MemcachedAdapter {
    public static final String PROPERTY_KEY_PREFIX = "h4m.adapter.spymemcached";
    public static final String HOST_PROPERTY_KEY = PROPERTY_KEY_PREFIX + ".hosts";
    public static final String HASH_ALGORITHM_PROPERTY_KEY = PROPERTY_KEY_PREFIX + ".hashalgorithm";
    public static final String OPERATION_TIMEOUT_MILLIS_PROPERTY_KEY = PROPERTY_KEY_PREFIX + ".operation.timeout.millis";
    public static final String TRANSCODER_PROPERTY_KEY = PROPERTY_KEY_PREFIX + ".transcoder";
    public static final String CACHE_KEY_PREFIX_PROPERTY_KEY = PROPERTY_KEY_PREFIX + ".cachekey.prefix";

    public static final String REGION_NAME_SQUENCE_SEPARATOR = "@";

    public static final int DEFAULT_REGION_SEQUENCE_EXPIRY_SECONDS = Hibernate4MemcachedRegionFactory.MEMCACHED_MAX_EPIRY_SECONDS;

    private Logger log = LoggerFactory.getLogger(SpyMemcachedAdapter.class);

    private MemcachedClientIF memcachedClient;

    private String cacheKeyPrefix = "";

    @Override
    public void init(OverridableReadOnlyProperties properties) {
        memcachedClient = createMemcachedClient(properties);
        cacheKeyPrefix = properties.getProperty(CACHE_KEY_PREFIX_PROPERTY_KEY, "");

        log.debug("Set cachekey prefix : [{}]", cacheKeyPrefix);
    }

    protected MemcachedClientIF createMemcachedClient(OverridableReadOnlyProperties properties) {
        ConnectionFactoryBuilder builder = createConnectionFactoryBuilder(properties);

        try {
            String addresses = properties.getRequiredProperty(HOST_PROPERTY_KEY);
            return new MemcachedClient(builder.build(), AddrUtil.getAddresses(addresses));
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /**
     * creating ConnectionFactoryBuilder object. Override thid method if you need.
     */
    protected ConnectionFactoryBuilder createConnectionFactoryBuilder(OverridableReadOnlyProperties properties) {
        ConnectionFactoryBuilder builder = new ConnectionFactoryBuilder();
        // BINARY Only!!! spymemcached incr/decr correctly supports only BINARY mode.
        builder.setProtocol(ConnectionFactoryBuilder.Protocol.BINARY);

        builder.setLocatorType(ConnectionFactoryBuilder.Locator.CONSISTENT);
        builder.setUseNagleAlgorithm(false);
        builder.setFailureMode(FailureMode.Redistribute);

        String hashAlgorithmProeprty = properties.getRequiredProperty(HASH_ALGORITHM_PROPERTY_KEY);
        builder.setHashAlg(DefaultHashAlgorithm.valueOf(hashAlgorithmProeprty));

        String operationTimeoutProperty = properties.getRequiredProperty(OPERATION_TIMEOUT_MILLIS_PROPERTY_KEY);
        builder.setOpTimeout(Long.parseLong(operationTimeoutProperty));

        String transcoderClassProperty = properties.getRequiredProperty(TRANSCODER_PROPERTY_KEY);
        builder.setTranscoder(createTranscoder(properties, transcoderClassProperty));
        return builder;
    }

    private Transcoder<Object> createTranscoder(OverridableReadOnlyProperties properties, String transcoderClassProperty) {
        try {
            @SuppressWarnings("unchecked")
            Class<InitializableTranscoder<Object>> transcoderClass = (Class<InitializableTranscoder<Object>>) Class.forName(transcoderClassProperty);
            InitializableTranscoder<Object> transcoder = transcoderClass.newInstance();
            transcoder.init(properties);
            return transcoder;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public void destroy() {
        memcachedClient.shutdown();
    }

    /**
     * Cache Region과 key를 조합하여 새로운 key를 생성한다.
     */
    protected String createRegionPrefixedKey(CacheNamespace cacheNamespace, String key) {
        String regionNameWithSequence = getRegionNameWithSequence(cacheNamespace);

        return regionNameWithSequence + ":" + key;
    }

    String getRegionNameWithSequence(CacheNamespace cacheNamespace) {
        if (!cacheNamespace.isNamespaceExpirationRequired()) {
            return cacheNamespace.getName();
        }

        String regionNameSequenceKey = prefixWithCacheKeyPrefix(getRegionNameSequenceKey(cacheNamespace));
        Long sequence = memcachedClient.incr(regionNameSequenceKey, 0L, System.currentTimeMillis(), DEFAULT_REGION_SEQUENCE_EXPIRY_SECONDS);
        log.debug("SpyMemcached getRegionNameWithSequence {}{}", regionNameSequenceKey, sequence);
        return cacheNamespace.getName() + REGION_NAME_SQUENCE_SEPARATOR + sequence;
    }

    String getRegionNameSequenceKey(CacheNamespace cacheNamespace) {
        return cacheNamespace.getName() + REGION_NAME_SQUENCE_SEPARATOR;
    }

    String prefixWithCacheKeyPrefix(String key) {
        if (cacheKeyPrefix == null || cacheKeyPrefix.length() == 0) {
            return key;
        }

        return cacheKeyPrefix + "." + key;
    }

    @Override
    public Object get(CacheNamespace cacheNamespace, String key) {
        String regionPrefixedKey = prefixWithCacheKeyPrefix(createRegionPrefixedKey(cacheNamespace, key));

        Object value = memcachedClient.get(regionPrefixedKey);
        log.debug("Spymemcached Get key [{}], hit {}.", regionPrefixedKey, value != null);
        return value;
    }

    @Override
    public void set(CacheNamespace cacheNamespace, String key, Object value, int expirySeconds) {
        String regionPrefixedKey = prefixWithCacheKeyPrefix(createRegionPrefixedKey(cacheNamespace, key));

        log.debug("Spymemcached Set key [{}], value [{}], expirySeconds [{}] .", regionPrefixedKey, value, expirySeconds);
        memcachedClient.set(regionPrefixedKey, expirySeconds, value);
    }

    @Override
    public void delete(CacheNamespace cacheNamespace, String key) {
        String regionPrefixedKey = prefixWithCacheKeyPrefix(createRegionPrefixedKey(cacheNamespace, key));
        log.debug("Spymemcached Delete key [{}].", regionPrefixedKey);

        memcachedClient.delete(regionPrefixedKey);
    }

    @Override
    public long increaseCounter(CacheNamespace cacheNamespace, String key, long by, long defaultValue, int expirySeconds) {
        String regionPrefixedKey = prefixWithCacheKeyPrefix(createRegionPrefixedKey(cacheNamespace, key));
        long counterValue = memcachedClient.incr(regionPrefixedKey, by, defaultValue, expirySeconds);
        log.debug("Spymemcached increase counter key [{}] with by {} default value {} returns {}", regionPrefixedKey, by, defaultValue, counterValue);

        return counterValue;
    }

    @Override
    public long getCounter(CacheNamespace cacheNamespace, String key, long defaultValue, int expirySeconds) {
        String regionPrefixedKey = prefixWithCacheKeyPrefix(createRegionPrefixedKey(cacheNamespace, key));
        long counterValue = memcachedClient.incr(regionPrefixedKey, 0, defaultValue, expirySeconds);

        log.debug("Spymemcached get counter key [{}] with default value {} returns {}", regionPrefixedKey, defaultValue, counterValue);

        return counterValue;
    }

    @Override
    public void evictAll(CacheNamespace cacheNamespace) {
        if (!cacheNamespace.isNamespaceExpirationRequired()) {
            log.debug("Spymemcached region Evict {} requested but did nothing because regionExpirationRequired == false.", cacheNamespace);
            return;
        }

        String rergionPrefixedKey = prefixWithCacheKeyPrefix(getRegionNameSequenceKey(cacheNamespace));
        long nextSequence = memcachedClient.incr(rergionPrefixedKey, 1, System.currentTimeMillis(), DEFAULT_REGION_SEQUENCE_EXPIRY_SECONDS);
        log.debug("Spymemcached region Evicted prefix : {}, cacheNamespace: {}, nextSequence {}", rergionPrefixedKey, cacheNamespace, nextSequence);
    }
}