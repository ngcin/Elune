/**
 * Elune - Lightweight Forum Powered by Razor.
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


package com.elune.controller.api;

import com.elune.dal.DBManager;

import com.elune.entity.TopicEntity;
import com.elune.entity.UserEntity;
import com.elune.model.LongIdModel;
import com.elune.service.*;
import com.elune.constants.CoinRewards;

import com.fedepot.exception.HttpException;
import com.fedepot.ioc.annotation.FromService;
import com.fedepot.mvc.annotation.*;
import com.fedepot.mvc.controller.APIController;
import com.fedepot.mvc.http.Session;

import static com.elune.constants.UserLogType.*;
import static com.elune.constants.NotificationType.*;

/**
 * @author Touchumind
 */
@RoutePrefix("api/v1/usermetas")
public class UserMetaController extends APIController {

    private DBManager dbManager;

    @FromService
    private UserService userService;

    @FromService
    private UserMetaService userMetaService;

    @FromService
    private TopicService topicService;

    @FromService
    private UserLogMQService userLogMQService;

    @FromService
    private NotificationMQService notificationMQService;

    @FromService
    private BalanceMQService balanceMQService;

    public UserMetaController(DBManager dbManager) {

        this.dbManager = dbManager;
    }

    @HttpGet
    @Route("favorites")
    public void getFavorites(@QueryParam("page") int page, @QueryParam("pageSize") int pageSize) {

        try {

            Session session = Request().session();
            long uid = session == null || session.attribute("uid") == null ? 0 : session.attribute("uid");

            if (uid < 1) {
                throw new HttpException("尚未登录", 401);
            }

            Succeed(userMetaService.getFavorites(uid, page, pageSize));
        } catch (Exception e) {

            Fail(e);
        }
    }

    @HttpGet
    @Route("following/topics")
    public void getFollowingTopics(@QueryParam("page") int page, @QueryParam("pageSize") int pageSize) {

        try {

            Session session = Request().session();
            long uid = session == null || session.attribute("uid") == null ? 0 : session.attribute("uid");

            if (uid < 1) {
                throw new HttpException("尚未登录", 401);
            }

            Succeed(userMetaService.getFollowingTopics(uid, page, pageSize));
        } catch (Exception e) {

            Fail(e);
        }
    }

    @HttpPost
    @Route("following/topics")
    public void followTopic(@FromBody LongIdModel longIdModel) {

        try {

            Session session = Request().session();
            long uid = session == null || session.attribute("uid") == null ? 0 : session.attribute("uid");

            if (uid < 1) {
                throw new HttpException("尚未登录", 401);
            }

            UserEntity user = userService.getUserEntity(uid);

            if (user == null) {

                throw new HttpException("你必须登录才能关注话题", 401);
            }

            if (user.getStatus().equals(Byte.valueOf("0"))) {

                throw new HttpException("你没有权限关注话题(账户未激活或已禁用)", 403);
            }

            TopicEntity topicEntity = topicService.getTopicEntity(longIdModel.id);
            if (topicEntity == null || topicEntity.getStatus().equals(Byte.valueOf("0"))) {

                throw new HttpException("话题不存在或已被删除", 404);
            }

            boolean result = userMetaService.followTopic(uid, longIdModel.id);


            if (uid != topicEntity.getAuthorId()) {
                // log
                userLogMQService.createUserLog(uid, L_FOLLOW_TOPIC, "", "关注了话题《".concat(topicEntity.getTitle()).concat("》"), Request().getIp(), Request().getUa());

                // notification
                notificationMQService.createNotification(user.getUsername(), topicEntity.getAuthorName(), user.getUsername().concat("关注了你的话题《".concat(topicEntity.getTitle()).concat("》")), "", N_TOPIC_FOLLOW);

                // add balance for author
                balanceMQService.increaseBalance(topicEntity.getAuthorId(), CoinRewards.R_TOPIC_BE_FOLLOWED);
            }

            Succeed(result);
        } catch (Exception e) {

            Fail(e);
        }
    }

    @HttpDelete
    @Route("following/topics/{long:id}")
    public void unfollowTopic(long id) {

        try {

            Session session = Request().session();
            long uid = session == null || session.attribute("uid") == null ? 0 : session.attribute("uid");

            if (uid < 1) {
                throw new HttpException("尚未登录", 401);
            }

            UserEntity user = userService.getUserEntity(uid);

            if (user == null) {

                throw new HttpException("你必须登录才能取消关注话题", 401);
            }

            if (user.getStatus().equals(Byte.valueOf("0"))) {

                throw new HttpException("你没有权限取消关注话题(账户未激活或已禁用)", 403);
            }

            TopicEntity topicEntity = topicService.getTopicEntity(id);
            if (topicEntity == null || topicEntity.getStatus().equals(Byte.valueOf("0"))) {

                throw new HttpException("话题不存在或已被删除", 404);
            }

            boolean result = userMetaService.unfollowTopic(uid, id);

            if (uid != topicEntity.getAuthorId()) {

                // log
                userLogMQService.createUserLog(uid, L_UNFOLLOW_TOPIC, "", "取消了对话题《".concat(topicEntity.getTitle()).concat("》的关注"), Request().getIp(), Request().getUa());

                // notification
                notificationMQService.createNotification(user.getUsername(), topicEntity.getAuthorName(), user.getUsername().concat("取消了对你的话题《".concat(topicEntity.getTitle()).concat("》的关注")), "", N_TOPIC_UNFOLLOW);
            }

            Succeed(result);
        } catch (Exception e) {

            Fail(e);
        }
    }
}