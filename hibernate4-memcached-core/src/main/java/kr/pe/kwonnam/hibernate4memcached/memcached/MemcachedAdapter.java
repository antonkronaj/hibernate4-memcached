package kr.pe.kwonnam.hibernate4memcached.memcached;

import kr.pe.kwonnam.hibernate4memcached.util.OverridableReadOnlyProperties;

/**
 * Adapter for memcached operation.
 * <p/>
 * <p/>
 * When Hibernate Session factory closed, {@link #destroy} method will be called automatically.
 *
 * @author KwonNam Son (kwon37xi@gmail.com)
 */
public interface MemcachedAdapter {
    /**
     * Lifecycle callback to perform initialization.
     *
     * @param properties the defined cfg properties
     */
    void init(OverridableReadOnlyProperties properties);

    /**
     * Lifecycle callback to perform cleanup.
     */
    void destroy();

    Object get(CacheNamespace cacheNamespace, String key);

    void set(CacheNamespace cacheNamespace, String key, Object value, int expirySeconds);

    void delete(CacheNamespace cacheNamespace, String key);

    void evictAll(CacheNamespace cacheNamespace);
}
