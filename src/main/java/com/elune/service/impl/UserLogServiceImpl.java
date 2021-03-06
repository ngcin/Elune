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

import com.elune.dal.DBManager;
import com.elune.dao.UserlogMapper;
import com.elune.entity.UserlogEntity;
import com.elune.entity.UserlogEntityExample;
import com.elune.model.Pagination;
import com.elune.model.UserLog;
import com.elune.service.UserLogService;
import com.elune.utils.DateUtil;
import com.elune.utils.DozerMapperUtil;

import com.fedepot.ioc.annotation.FromService;
import com.fedepot.ioc.annotation.Service;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.elune.constants.UserLogType.*;

@Slf4j
@Service
public class UserLogServiceImpl implements UserLogService {

    @FromService
    private DBManager dbManager;

    @Override
    public long createUserLog(long uid, String username, byte type, String before, String after, String link, String ip, String ua) {

        try (SqlSession sqlSession = dbManager.getSqlSession()) {

            UserlogMapper mapper = sqlSession.getMapper(UserlogMapper.class);
            UserlogEntity entity = UserlogEntity.builder().uid(uid).username(username).type(type).ip(ip).ua(ua).beforeStatus(before).afterStatus(after).link(link).createTime(DateUtil.getTimeStamp()).build();

            mapper.insertSelective(entity);
            sqlSession.commit();

            return entity.getId();

        } catch (Exception e) {

            log.error("Insert user log record failed", e);
            throw e;
        }
    }

    @Override
    public Pagination<UserLog> getUserLogs(long uid, byte type, int page, int pageSize, String orderClause) {

        try (SqlSession sqlSession = dbManager.getSqlSession()) {

            UserlogMapper mapper = sqlSession.getMapper(UserlogMapper.class);
            UserlogEntityExample entityExample = UserlogEntityExample.builder().oredCriteria(new ArrayList<>()).offset((page - 1) * pageSize).limit(pageSize).orderByClause(orderClause).build();

            entityExample.or().andUidEqualTo(uid).andTypeEqualTo(type);

            List<UserlogEntity> entities = mapper.selectByExample(entityExample);
            List<UserLog> logs = assembleUserLogs(entities);

            UserlogEntityExample countEntityExample = UserlogEntityExample.builder().oredCriteria(new ArrayList<>()).build();
            countEntityExample.or().andUidEqualTo(uid).andTypeEqualTo(type);

            long total = mapper.countByExample(countEntityExample);

            return new Pagination<>(total, page, pageSize, logs);
        }
    }

    @Override
    public Pagination<UserLog> getUserActivities(long uid, int page, int pageSize, String orderClause) {

        return getUserActivities(Collections.singletonList(uid), page, pageSize, orderClause);
    }

    @Override
    public Pagination<UserLog> getUserActivities(List<Long> uids, int page, int pageSize, String orderClause) {
        List<Byte> activityTypes = new ArrayList<>(Arrays.asList(L_CREATE_TOPIC, L_CREATE_POST, L_FAVORITE_TOPIC, L_LIKE_TOPIC, L_UPLOAD_AVATAR, L_FOLLOW_TOPIC, L_FOLLOW_USER));
        try (SqlSession sqlSession = dbManager.getSqlSession()) {

            UserlogMapper mapper = sqlSession.getMapper(UserlogMapper.class);
            UserlogEntityExample entityExample = UserlogEntityExample.builder().oredCriteria(new ArrayList<>()).offset((page - 1) * pageSize).limit(pageSize).orderByClause(orderClause).build();

            entityExample.or().andUidIn(uids).andTypeIn(activityTypes);

            List<UserlogEntity> entities = mapper.selectByExample(entityExample);
            List<UserLog> logs = assembleUserLogs(entities);

            UserlogEntityExample countEntityExample = UserlogEntityExample.builder().oredCriteria(new ArrayList<>()).build();
            countEntityExample.or().andUidIn(uids).andTypeIn(activityTypes);

            long total = mapper.countByExample(countEntityExample);

            return new Pagination<>(total, page, pageSize, logs);
        }
    }

    private List<UserLog> assembleUserLogs(List<UserlogEntity> entities) {

        List<UserLog> logs = new ArrayList<>(entities.size());

        entities.forEach(entity -> {
            logs.add(DozerMapperUtil.map(entity, UserLog.class));
        });

        return logs;
    }
}
