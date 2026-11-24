package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.dto.CustomerForm;
import com.jshz.expert.entity.Customer;
import com.jshz.expert.entity.CustomerRelation;
import com.jshz.expert.mapper.CustomerMapper;
import com.jshz.expert.mapper.CustomerRelationMapper;
import com.jshz.expert.mapper.CustomerRelationTypeMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerInfoRegressionTest {
    private final CustomerMapper customers = mock(CustomerMapper.class);
    private final CustomerRelationMapper relations = mock(CustomerRelationMapper.class);
    private final CustomerRelationService relationService =
            new CustomerRelationService(relations, mock(CustomerRelationTypeMapper.class), customers);
    private final CustomerService customerService = new CustomerService(customers, null, null, relationService);

    @BeforeAll
    static void initializeMapperMetadata() {
        var mapper = new MapperBuilderAssistant(new MybatisConfiguration(), "customer-test");
        TableInfoHelper.initTableInfo(mapper, Customer.class);
        TableInfoHelper.initTableInfo(mapper, CustomerRelation.class);
    }

    @Test
    void writesBlankAsSqlNullAndLeavesOmittedFieldsOutOfUpdate() {
        Customer original = activeCustomer(1L);
        original.setWorkUnit("旧单位");
        Customer stored = activeCustomer(1L);
        when(customers.selectById(1L)).thenReturn(original, stored);
        when(customers.update(isNull(), any())).thenAnswer(call -> {
            LambdaUpdateWrapper<Customer> update = call.getArgument(1);
            assertTrue(update.getSqlSet().startsWith("work_unit="));
            assertFalse(update.getSqlSet().contains("work_position="));
            assertTrue(update.getParamNameValuePairs().containsValue(null));
            assertTrue(update.getSqlSegment().contains("deleted ="));
            return 1;
        });

        CustomerForm form = new CustomerForm();
        form.setWorkUnit("  ");
        assertSame(stored, customerService.update(1L, form, null));
        verify(customers).update(isNull(), any());
    }

    @Test
    void rejectsUpdatesToDeletedCustomer() {
        Customer deleted = activeCustomer(1L);
        deleted.setDeleted(1);
        when(customers.selectById(1L)).thenReturn(deleted);
        assertThrows(BusinessException.class, () -> customerService.update(1L, new CustomerForm(), null));
        verify(customers, never()).update(any(), any());
    }

    @Test
    void checksCreationLimitsBeforeWriting() {
        CustomerForm form = new CustomerForm();
        form.setName("甲".repeat(CustomerService.NAME_MAX_LENGTH + 1));
        assertThrows(BusinessException.class, () -> customerService.create(form, null));
        form.setName("甲".repeat(CustomerService.NAME_MAX_LENGTH));
        form.setRelations(Collections.nCopies(CustomerService.CREATE_RELATION_LIMIT + 1, new CustomerForm.RelationItem()));
        assertThrows(BusinessException.class, () -> customerService.create(form, null));
        verifyNoInteractions(customers);
    }

    @Test
    void countsOnlyRelationshipsWithTwoVisibleEndpoints() {
        when(relations.selectList(any())).thenReturn(List.of(relation(1, 2), relation(1, 3), relation(1, 4)));
        when(customers.selectList(any())).thenReturn(List.of(activeCustomer(1), activeCustomer(2)));
        assertEquals(Map.of(1L, 1L, 2L, 1L), relationService.countByCustomerIds(List.of(1L, 2L, 3L)));
    }

    @Test
    void keepsPreviouslyRelatedCustomersInTargetSearch() {
        when(customers.selectById(1L)).thenReturn(activeCustomer(1));
        when(customers.selectPage(any(Page.class), any())).thenAnswer(call -> {
            LambdaQueryWrapper<Customer> search = call.getArgument(1);
            assertTrue(search.getSqlSegment().contains("id <>"));
            assertFalse(search.getSqlSegment().contains("NOT IN"));
            Page<Customer> page = call.getArgument(0);
            return page.setRecords(List.of(activeCustomer(2)));
        });
        assertEquals(2L, relationService.searchTargets(1L, "甲", 1, 10).getRecords().get(0).getId());
        verifyNoInteractions(relations);
    }

    @Test
    void globalGraphFlagsTruncationOnlyAboveItsLimit() {
        List<Customer> records = LongStream.rangeClosed(1, 501)
                .mapToObj(CustomerInfoRegressionTest::activeCustomer).toList();
        when(customers.selectList(any())).thenReturn(records.subList(0, 500), records);
        when(relations.selectList(any())).thenReturn(List.of());
        assertEquals(false, relationService.networkGraph().get("truncated"));
        Map<String, Object> truncated = relationService.networkGraph();
        assertEquals(true, truncated.get("truncated"));
        assertEquals(500, ((List<?>) truncated.get("nodes")).size());
    }

    private static Customer activeCustomer(long id) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setDeleted(0);
        return customer;
    }

    private static CustomerRelation relation(long from, long to) {
        CustomerRelation relation = new CustomerRelation();
        relation.setFromCustomerId(from);
        relation.setToCustomerId(to);
        return relation;
    }
}
