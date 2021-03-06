package kr.pe.kwonnam.hibernate4memcached.example.testcache;

import org.hibernate.cache.CacheException;

/**
 * @author Strong Liu <stliu@hibernate.org>
 */
class TransactionalCollectionRegionAccessStrategy extends BaseCollectionRegionAccessStrategy {
	TransactionalCollectionRegionAccessStrategy(CollectionRegionImpl region) {
		super( region );
	}




	/**
	 * {@inheritDoc}
	 */
	@Override
	public void remove(Object key) throws CacheException {
		evict( key );
	}


}
