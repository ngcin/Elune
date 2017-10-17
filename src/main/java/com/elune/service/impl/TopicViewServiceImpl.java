/**
 * Elune - Lightweight Forum Powered by Razor
 * Copyright (C) 2017, Touchumind<chinash2010@gmail.com>
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */


package com.elune.service.impl;

import com.elune.dal.RedisManager;
import com.elune.mq.Consumer;
import com.elune.mq.MessageQueue;
import com.elune.mq.Producer;
import com.elune.service.TopicService;
import com.elune.service.TopicViewService;
import com.elune.task.TopicViewCountTask;

import com.elune.utils.DateUtil;
import com.fedepot.ioc.annotation.ForInject;
import com.fedepot.ioc.annotation.FromService;
import com.fedepot.ioc.annotation.Service;
import com.fedepot.mvc.json.GsonFactory;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;

@Slf4j
@Service(sington = true)
public class TopicViewServiceImpl implements TopicViewService {

    private Producer producer;

    @FromService
    private RedisManager redisManager;

    @FromService
    private TopicService topicService;

    @ForInject
    public TopicViewServiceImpl(MessageQueue messageQueue) {

        String topic = "QUEUETOPIC::TOPICVIEWCOUNT";
        producer = new Producer(messageQueue, topic);
        Consumer consumer = new Consumer(messageQueue, topic);

        consumer.consume(message -> {
            Gson gson = GsonFactory.getGson();

            TopicViewCountTask task = gson.fromJson(message, TopicViewCountTask.class);
            executeTask(task);
        });

        consumer.up();
    }

    @Override
    public void increaseViews(long topicId, int count) {

        TopicViewCountTask task = TopicViewCountTask.builder().topicId(Long.toString(topicId)).views(count).writeTime(0).build();
        producer.produce(GsonFactory.getGson().toJson(task));
    }

    @Override
    public void increaseViews(long topicId) {

        increaseViews(topicId, 1);
    }

    private void executeTask(TopicViewCountTask task) {

        if (task.getWriteTime() == 0) {

            redisManager.getJedis().incrBy(task.getKey(), task.getViews());
            task.setWriteTime(DateUtil.getTimeStamp());
            producer.produce(GsonFactory.getGson().toJson(task));
        } else {
            int now = DateUtil.getTimeStamp();
            if (now - task.getWriteTime() > 60) {

                Jedis jedis = redisManager.getJedis();
                int count = Integer.valueOf(jedis.get(task.getKey()));
                jedis.del(task.getKey());
                redisManager.retureRes(jedis);

                topicService.updateTopicViews(Long.valueOf(task.getTopicId()), count);
            } else {

                producer.produce(GsonFactory.getGson().toJson(task));
            }
        }
    }
}
