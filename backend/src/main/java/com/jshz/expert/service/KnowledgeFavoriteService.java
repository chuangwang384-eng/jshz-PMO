package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.KnowledgeDocument;
import com.jshz.expert.entity.KnowledgeFavorite;
import com.jshz.expert.mapper.KnowledgeDocumentMapper;
import com.jshz.expert.mapper.KnowledgeFavoriteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识文档收藏服务（C3）。
 * 收藏/取消收藏仅能操作本人数据（userId 一律取当前登录用户，接口层不接收他人 userId）。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeFavoriteService extends ServiceImpl<KnowledgeFavoriteMapper, KnowledgeFavorite> {

    private final KnowledgeDocumentMapper documentMapper;

    /**
     * 收藏文档（幂等：已收藏直接返回成功）。
     */
    public void favorite(Long docId, Long userId) {
        if (docId == null || userId == null) {
            throw new BusinessException("参数不合法");
        }
        KnowledgeDocument doc = documentMapper.selectById(docId);
        if (doc == null) {
            throw new BusinessException("文档不存在");
        }
        if (exists(docId, userId)) {
            return;
        }
        KnowledgeFavorite f = new KnowledgeFavorite();
        f.setDocId(docId);
        f.setUserId(userId);
        try {
            this.save(f);
        } catch (DuplicateKeyException e) {
            // 并发重复收藏：唯一约束兜底，视为成功
        }
    }

    /**
     * 取消收藏（幂等：未收藏也返回成功）。
     */
    public void unfavorite(Long docId, Long userId) {
        if (docId == null || userId == null) {
            throw new BusinessException("参数不合法");
        }
        this.remove(new LambdaQueryWrapper<KnowledgeFavorite>()
                .eq(KnowledgeFavorite::getDocId, docId)
                .eq(KnowledgeFavorite::getUserId, userId));
    }

    /**
     * 单文档收藏状态。
     */
    public boolean exists(Long docId, Long userId) {
        if (docId == null || userId == null) {
            return false;
        }
        return this.count(new LambdaQueryWrapper<KnowledgeFavorite>()
                .eq(KnowledgeFavorite::getDocId, docId)
                .eq(KnowledgeFavorite::getUserId, userId)) > 0;
    }

    /**
     * 批量查询用户在给定文档集合中已收藏的 docId（列表接口 isFavorite 批量填充用）。
     */
    public Set<Long> favoriteDocIds(Long userId, List<Long> docIds) {
        if (userId == null || docIds == null || docIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<KnowledgeFavorite> rows = this.list(new LambdaQueryWrapper<KnowledgeFavorite>()
                .eq(KnowledgeFavorite::getUserId, userId)
                .in(KnowledgeFavorite::getDocId, docIds));
        Set<Long> result = new HashSet<>();
        for (KnowledgeFavorite f : rows) {
            result.add(f.getDocId());
        }
        return result;
    }

    /**
     * 我的收藏列表（按收藏时间倒序，返回文档信息 + 收藏时间）。
     * 文档可能已被删除（收藏残留）：过滤掉查不到的。
     */
    public List<Map<String, Object>> myFavorites(Long userId) {
        if (userId == null) {
            return new ArrayList<>();
        }
        List<KnowledgeFavorite> favorites = this.list(new LambdaQueryWrapper<KnowledgeFavorite>()
                .eq(KnowledgeFavorite::getUserId, userId)
                .orderByDesc(KnowledgeFavorite::getCreateTime));
        if (favorites.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> docIds = favorites.stream().map(KnowledgeFavorite::getDocId).toList();
        Map<Long, KnowledgeDocument> docMap = new HashMap<>();
        for (KnowledgeDocument doc : documentMapper.selectBatchIds(docIds)) {
            docMap.put(doc.getId(), doc);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (KnowledgeFavorite f : favorites) {
            KnowledgeDocument doc = docMap.get(f.getDocId());
            if (doc == null) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("doc", doc);
            item.put("favoriteTime", f.getCreateTime());
            result.add(item);
        }
        return result;
    }

    /**
     * 文档的全部收藏者用户ID（更新通知用）。
     */
    public List<Long> userIdsOfDoc(Long docId) {
        if (docId == null) {
            return Collections.emptyList();
        }
        List<KnowledgeFavorite> rows = this.list(new LambdaQueryWrapper<KnowledgeFavorite>()
                .eq(KnowledgeFavorite::getDocId, docId));
        List<Long> result = new ArrayList<>();
        for (KnowledgeFavorite f : rows) {
            result.add(f.getUserId());
        }
        return result;
    }
}
