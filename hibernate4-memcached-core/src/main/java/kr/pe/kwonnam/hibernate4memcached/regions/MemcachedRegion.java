package kr.pe.kwonnam.hibernate4memcached.regions;

import kr.pe.kwonnam.hibernate4memcached.memcached.CacheNamespace;
import kr.pe.kwonnam.hibernate4memcached.memcached.MemcachedAdapter;
import kr.pe.kwonnam.hibernate4memcached.util.MemcachedTimestamper;
import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.CacheDataDescription;
import org.hibernate.cache.spi.Region;
import org.hibernate.cfg.Settings;

import java.util.Map;
import java.util.Properties;

/**
 * @author KwonNam Son (kwon37xi@gmail.com)
 */
public class MemcachedRegion implements Region {
    public static final int UNKNOWN = -1;

    private static final int DEFAULT_CACHE_LOCK_TIMEOUT_MILLIS = 60 * 1000;


    private CacheNamespace cacheNamespace;

    private Properties properties;

    private CacheDataDescription metadata;

    private Settings settings;

    private MemcachedAdapter memcachedAdapter;

    private MemcachedTimestamper memcachedTimestamper;

    public MemcachedRegion(CacheNamespace cacheNamespace, Properties properties, CacheDataDescription metadata, Settings settings,
                           MemcachedAdapter memcachedAdapter, MemcachedTimestamper memcachedTimestamper) {
        this.cacheNamespace = cacheNamespace;
        this.properties = properties;
        this.metadata = metadata;
        this.settings = settings;
        this.memcachedAdapter = memcachedAdapter;
        this.memcachedTimestamper = memcachedTimestamper;
    }

    public CacheNamespace getCacheNamespace() {
        return cacheNamespace;
    }

    public Properties getProperties() {
        return properties;
    }

    public CacheDataDescription getMetadata() {
        return metadata;
    }

    public Settings getSettings() {
        return settings;
    }

    public MemcachedAdapter getMemcachedAdapter() {
        return memcachedAdapter;
    }

    public MemcachedTimestamper getMemcachedTimestamper() {
        return memcachedTimestamper;
    }

    @Override
    public String getName() {
        return cacheNamespace.getRegionName();
    }

    /**
     * 여기서는 Hibernate SessionFactory가 close될 때region에 대한 후처리 작업을 하는 것이지
     * region 자체를 삭제하면 안 될 것으로 보인다.
     * <p />
     * 웹 서버 여러대에서 작업 할 경우 여기서 region을 삭제하면 한 대의 웹서버가 내려갈 때
     * 모든 region들을 evict 시켜버리게 된다.
     *
     * @see org.hibernate.cache.spi.Region#destroy()
     */
    @Override
    public void destroy() throws CacheException {
        // do nothing. NEVER evict cache region!!
    }

    @Override
    public boolean contains(Object key) {
        return false;
    }

    @Override
    public long getSizeInMemory() {
        return UNKNOWN;
    }

    @Override
    public long getElementCountInMemory() {
        return UNKNOWN;
    }

    @Override
    public long getElementCountOnDisk() {
        return UNKNOWN;
    }

    /**
     * Unsupported.
     */
    @Override
    public Map toMap() {
        return null;
    }

    @Override
    public long nextTimestamp() {
        return memcachedTimestamper.next();
    }

    @Override
    public int getTimeout() {
        // no meaning here.
        return DEFAULT_CACHE_LOCK_TIMEOUT_MILLIS;
    }
}