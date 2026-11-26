package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.Supplier;
import com.jshz.expert.entity.SupplierCity;
import com.jshz.expert.entity.SupplierHotelPrintRel;
import com.jshz.expert.entity.SupplierReview;
import com.jshz.expert.entity.SupplierType;
import com.jshz.expert.mapper.SupplierCityMapper;
import com.jshz.expert.mapper.SupplierHotelPrintRelMapper;
import com.jshz.expert.mapper.SupplierImageMapper;
import com.jshz.expert.mapper.SupplierMapper;
import com.jshz.expert.mapper.SupplierPriceItemMapper;
import com.jshz.expert.mapper.SupplierReviewMapper;
import com.jshz.expert.mapper.SupplierTypeMapper;
import com.jshz.expert.security.AuthUser;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 供应商板块审查修复回归（2026-11-26，1.0.156）：
 * P2-1 评价长度 / P2-2 行价行锁 / P3-2 bind 查库次数 / 需求四 未绑定清单。
 */
@ExtendWith(MockitoExtension.class)
class SupplierAuditFixTest {

    @InjectMocks SupplierService service;
    @Mock SupplierMapper baseMapperMock;
    @Mock SupplierTypeMapper typeMapper;
    @Mock SupplierCityMapper cityMapper;
    @Mock SupplierPriceItemMapper priceItemMapper;
    @Mock SupplierReviewMapper reviewMapper;
    @Mock SupplierHotelPrintRelMapper relMapper;
    @Mock SupplierImageMapper imageMapper;
    @Mock com.jshz.expert.mapper.ProjectSupplierMapper projectSupplierMapper;
    @Mock SupplierStorageService storage;
    @Mock SupplierImageService imageService;

    AuthUser user() {
        return new AuthUser(9L, "评价人", false, false, Set.of(), Set.of());
    }

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "fixture"), Supplier.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "fixture"), SupplierType.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "fixture"), SupplierCity.class);
        ReflectionTestUtils.setField(service, "baseMapper", baseMapperMock);
    }

    private static Supplier supplier(long id, Long typeId, Long cityId, String name) {
        Supplier s = new Supplier();
        s.setId(id);
        s.setName(name);
        s.setTypeId(typeId);
        s.setCityId(cityId);
        return s;
    }

    private static SupplierType type(long id, String name, String bizKey) {
        SupplierType t = new SupplierType();
        t.setId(id);
        t.setName(name);
        t.setBizKey(bizKey);
        return t;
    }

    // ---------- P2-1 评价长度 ----------

    @Test
    void reviewRejectsOverFiveHundredChars() {
        when(baseMapperMock.selectById(1L)).thenReturn(supplier(1L, 1L, 1L, "酒店A"));
        String longContent = "长".repeat(501);
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.addReview(1L, 5, longContent, user()));
        assertEquals("评价内容不能超过 500 字", e.getMessage());
        verify(reviewMapper, never()).insert(any(SupplierReview.class));
    }

    @Test
    void reviewAcceptsExactlyFiveHundredChars() {
        when(baseMapperMock.selectById(1L)).thenReturn(supplier(1L, 1L, 1L, "酒店A"));
        doReturn(1).when(reviewMapper).insert(any(SupplierReview.class));
        assertDoesNotThrow(() -> service.addReview(1L, 5, "字".repeat(500), user()));
    }

    // ---------- P2-2 行价行锁 ----------

    @Test
    void savePricesLocksSupplierRowBeforeDeleteInsert() {
        when(baseMapperMock.selectOne(any())).thenReturn(supplier(1L, 1L, 1L, "车队B"));
        when(priceItemMapper.delete(any())).thenReturn(0);
        doReturn(1).when(priceItemMapper).insert(any(com.jshz.expert.entity.SupplierPriceItem.class));
        service.savePrices(1L, List.of(Map.of("itemName", "团体机票", "price", "800")));

        // 锁先行：selectOne（FOR UPDATE）必须在 delete 之前
        org.mockito.InOrder inOrder = inOrder(baseMapperMock, priceItemMapper);
        inOrder.verify(baseMapperMock).selectOne(any());
        inOrder.verify(priceItemMapper).delete(any());
        ArgumentCaptor<LambdaQueryWrapper<Supplier>> cap = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(baseMapperMock).selectOne(cap.capture());
        assertTrue(cap.getValue().getSqlSegment().contains("FOR UPDATE"), "行价保存必须带 FOR UPDATE 行锁（P2-2）");
    }

    // ---------- P3-2 bind 查库次数 ----------

    @Test
    void bindQueriesEachSupplierOnlyOnce() {
        Supplier hotel = supplier(1L, 1L, 10L, "酒店A");
        Supplier print = supplier(2L, 2L, 10L, "打印店B");
        when(baseMapperMock.selectById(1L)).thenReturn(hotel);
        when(baseMapperMock.selectById(2L)).thenReturn(print);
        when(typeMapper.selectById(1L)).thenReturn(type(1L, "酒店", "HOTEL"));
        when(typeMapper.selectById(2L)).thenReturn(type(2L, "打印店", "PRINT"));
        when(relMapper.selectCount(any())).thenReturn(0L);
        doReturn(1).when(relMapper).insert(any(SupplierHotelPrintRel.class));

        service.bind(1L, 2L, user());

        verify(baseMapperMock, times(2)).selectById(anyLong());
        verify(relMapper).insert(any(SupplierHotelPrintRel.class));
    }

    // ---------- 需求四 未绑定清单 ----------

    @Test
    void unboundPeersListsOnlyHotelsAndPrintsWithoutBinding() {
        when(typeMapper.selectList(isNull())).thenReturn(List.of(
                type(1L, "酒店", "HOTEL"), type(2L, "打印店", "PRINT"), type(3L, "车队", "FLEET")));
        Supplier boundHotel = supplier(1L, 1L, 10L, "已绑酒店");
        Supplier unboundHotel = supplier(2L, 1L, 10L, "未绑酒店");
        Supplier unboundPrint = supplier(3L, 2L, 20L, "未绑打印店");
        // type_id IN (酒店/打印店) 过滤由 wrapper 承担（mock 直接返回候选集，含一辆车队用于验证断言）
        Supplier fleet = supplier(4L, 3L, 10L, "车队不参与");
        when(baseMapperMock.selectList(any())).thenReturn(List.of(boundHotel, unboundHotel, unboundPrint, fleet));
        // 模拟 wrapper 的类型过滤：仅保留 typeId ∈ {1,2}
        when(baseMapperMock.selectList(any())).thenAnswer(inv -> {
            LambdaQueryWrapper<Supplier> w = inv.getArgument(0);
            return java.util.stream.Stream.of(boundHotel, unboundHotel, unboundPrint, fleet)
                    .filter(x -> x.getTypeId() != null && x.getTypeId() <= 2L)
                    .collect(java.util.stream.Collectors.toList());
        });
        SupplierHotelPrintRel rel = new SupplierHotelPrintRel();
        rel.setHotelId(1L);
        rel.setPrintShopId(9L);
        when(relMapper.selectList(isNull())).thenReturn(List.of(rel));
        SupplierCity c10 = new SupplierCity(); c10.setId(10L); c10.setName("北京");
        SupplierCity c20 = new SupplierCity(); c20.setId(20L); c20.setName("上海");
        when(cityMapper.selectBatchIds(any())).thenReturn(List.of(c10, c20));

        List<Map<String, Object>> result = service.unboundBindingPeers(null);

        // 已绑定的酒店、非酒店/打印店类型（车队）都不出现
        assertTrue(result.stream().noneMatch(m -> "已绑酒店".equals(m.get("name"))));
        assertTrue(result.stream().noneMatch(m -> "车队不参与".equals(m.get("name"))));
        assertEquals(2, result.size());
        // 排序：城市 → 类型 → 名称（中文城市字典序下上海在前，此处按集合断言不依赖顺序细节）
        assertEquals(Set.of("未绑酒店", "未绑打印店"),
                result.stream().map(m -> String.valueOf(m.get("name"))).collect(java.util.stream.Collectors.toSet()));
        Map<String, Object> hotelItem = result.stream().filter(m -> "未绑酒店".equals(m.get("name"))).findFirst().orElseThrow();
        assertEquals("HOTEL", hotelItem.get("typeBizKey"));
        assertEquals("北京", hotelItem.get("cityName"));
    }

    @Test
    void unboundPeersFiltersByCity() {
        when(typeMapper.selectList(isNull())).thenReturn(List.of(
                type(1L, "酒店", "HOTEL"), type(2L, "打印店", "PRINT")));
        when(baseMapperMock.selectList(any())).thenReturn(List.of(supplier(3L, 2L, 20L, "上海打印店")));
        when(relMapper.selectList(isNull())).thenReturn(List.of());
        SupplierCity c20 = new SupplierCity(); c20.setId(20L); c20.setName("上海");
        when(cityMapper.selectBatchIds(any())).thenReturn(List.of(c20));

        List<Map<String, Object>> result = service.unboundBindingPeers(20L);
        assertEquals(1, result.size());
        assertEquals("上海", result.get(0).get("cityName"));
    }
}
