package com.commafeed.backend.cache;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Alternative;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Pipeline;

import com.commafeed.backend.model.Feed;
import com.commafeed.backend.model.FeedCategory;
import com.commafeed.backend.model.FeedSubscription;
import com.commafeed.backend.model.Models;
import com.commafeed.backend.model.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.google.api.client.util.Lists;

@Alternative
@ApplicationScoped
public class RedisCacheService extends CacheService {

	private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);
	private static ObjectMapper mapper = new ObjectMapper();
	private static CollectionType TYPE_LIST_CATEGORIES = mapper.getTypeFactory().constructCollectionType(List.class, FeedCategory.class);
	private static CollectionType TYPE_LIST_SUBSCRIPTIONS = mapper.getTypeFactory().constructCollectionType(List.class,
			FeedSubscription.class);

	private JedisPool pool = new JedisPool(new JedisPoolConfig(), "localhost");

	@Override
	public List<String> getLastEntries(Feed feed) {
		List<String> list = Lists.newArrayList();
		Jedis jedis = pool.getResource();
		try {
			String key = buildRedisEntryKey(feed);
			Set<String> members = jedis.smembers(key);
			for (String member : members) {
				list.add(member);
			}
		} finally {
			pool.returnResource(jedis);
		}
		return list;
	}

	@Override
	public void setLastEntries(Feed feed, List<String> entries) {
		Jedis jedis = pool.getResource();
		try {
			String key = buildRedisEntryKey(feed);

			Pipeline pipe = jedis.pipelined();
			pipe.del(key);
			for (String entry : entries) {
				pipe.sadd(key, entry);
			}
			pipe.expire(key, (int) TimeUnit.DAYS.toSeconds(7));
			pipe.sync();
		} finally {
			pool.returnResource(jedis);
		}
	}

	@Override
	public List<FeedCategory> getUserCategories(User user) {
		List<FeedCategory> cats = null;
		Jedis jedis = pool.getResource();
		try {
			String key = buildRedisUserCategoriesKey(user);
			String json = jedis.get(key);
			if (json != null) {
				cats = mapper.readValue(json, TYPE_LIST_CATEGORIES);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		} finally {
			pool.returnResource(jedis);
		}
		return cats;
	}

	@Override
	public void setUserCategories(User user, List<FeedCategory> categories) {
		Jedis jedis = pool.getResource();
		try {
			String key = buildRedisUserCategoriesKey(user);

			Pipeline pipe = jedis.pipelined();
			pipe.del(key);
			pipe.set(key, mapper.writeValueAsString(categories));
			pipe.expire(key, (int) TimeUnit.MINUTES.toSeconds(30));
			pipe.sync();
		} catch (JsonProcessingException e) {
			log.error(e.getMessage(), e);
		} finally {
			pool.returnResource(jedis);
		}
	}

	@Override
	public List<FeedSubscription> getUserSubscriptions(User user) {
		List<FeedSubscription> subs = null;
		Jedis jedis = pool.getResource();
		try {
			String key = buildRedisUserSubscriptionsKey(user);
			String json = jedis.get(key);
			if (json != null) {
				subs = mapper.readValue(json, TYPE_LIST_SUBSCRIPTIONS);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		} finally {
			pool.returnResource(jedis);
		}
		return subs;
	}

	@Override
	public void setUserSubscriptions(User user, List<FeedSubscription> subs) {
		Jedis jedis = pool.getResource();
		try {
			String key = buildRedisUserSubscriptionsKey(user);

			Pipeline pipe = jedis.pipelined();
			pipe.del(key);
			pipe.set(key, mapper.writeValueAsString(subs));
			pipe.expire(key, (int) TimeUnit.MINUTES.toSeconds(30));
			pipe.sync();
		} catch (JsonProcessingException e) {
			log.error(e.getMessage(), e);
		} finally {
			pool.returnResource(jedis);
		}
	}

	@Override
	public Long getUnreadCount(FeedSubscription sub) {
		Long count = null;
		Jedis jedis = pool.getResource();
		try {
			String key = buildRedisUnreadCountKey(sub);
			String countString = jedis.get(key);
			if (countString != null) {
				count = Long.valueOf(countString);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		} finally {
			pool.returnResource(jedis);
		}
		return count;
	}

	@Override
	public void setUnreadCount(FeedSubscription sub, Long count) {
		Jedis jedis = pool.getResource();
		try {
			String key = buildRedisUnreadCountKey(sub);

			Pipeline pipe = jedis.pipelined();
			pipe.del(key);
			pipe.set(key, String.valueOf(count));
			pipe.expire(key, (int) TimeUnit.MINUTES.toSeconds(30));
			pipe.sync();
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		} finally {
			pool.returnResource(jedis);
		}
	}

	@Override
	public void invalidateUserCategories(User user) {
		Jedis jedis = pool.getResource();
		try {
			String key = buildRedisUserCategoriesKey(user);
			jedis.del(key);
		} finally {
			pool.returnResource(jedis);
		}
	}

	@Override
	public void invalidateUserSubscriptions(User user) {
		Jedis jedis = pool.getResource();
		try {
			String key = buildRedisUserSubscriptionsKey(user);
			jedis.del(key);
		} finally {
			pool.returnResource(jedis);
		}
	}

	@Override
	public void invalidateUnreadCount(FeedSubscription... subs) {
		Jedis jedis = pool.getResource();
		try {
			Pipeline pipe = jedis.pipelined();
			if (subs != null) {
				for (FeedSubscription sub : subs) {
					String key = buildRedisUnreadCountKey(sub);
					pipe.del(key);
				}
			}
			pipe.sync();
		} finally {
			pool.returnResource(jedis);
		}
	}

	private String buildRedisEntryKey(Feed feed) {
		return "f:" + Models.getId(feed);
	}

	private String buildRedisUserCategoriesKey(User user) {
		return "c:" + Models.getId(user);
	}

	private String buildRedisUserSubscriptionsKey(User user) {
		return "s:" + Models.getId(user);
	}

	private String buildRedisUnreadCountKey(FeedSubscription sub) {
		return "u:" + Models.getId(sub);
	}

}
