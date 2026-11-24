package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.controller.CustomerController;
import com.jshz.expert.dto.CustomerEmploymentForm;
import com.jshz.expert.entity.Customer;
import com.jshz.expert.entity.CustomerEmployment;
import com.jshz.expert.mapper.CustomerEmploymentMapper;
import com.jshz.expert.mapper.CustomerMapper;
import com.jshz.expert.mapper.CustomerRelationMapper;
import com.jshz.expert.mapper.CustomerRelationTypeMapper;
import com.jshz.expert.security.AuthUser;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerFeaturesTest {
    private final CustomerMapper customers = mock(CustomerMapper.class);
    private final CustomerRelationService relations = new CustomerRelationService(
            mock(CustomerRelationMapper.class), mock(CustomerRelationTypeMapper.class), customers);
    private final CustomerService customerService = new CustomerService(customers, null, null, relations);
    private final CustomerEmploymentMapper history = mock(CustomerEmploymentMapper.class);
    private final CustomerEmploymentService employment = new CustomerEmploymentService(customerService, history);

    @BeforeAll
    static void initializeSqlMetadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "customer-features-test");
        TableInfoHelper.initTableInfo(assistant, Customer.class);
        TableInfoHelper.initTableInfo(assistant, CustomerEmployment.class);
    }

    @Test
    void duplicateCheckRequiresBothFieldsAndUsesEffectiveExactMatches() {
        assertTrue(customerService.findDuplicates("同名", " ").isEmpty());
        verifyNoInteractions(customers);
        when(customers.selectList(any())).thenAnswer(call -> {
            LambdaQueryWrapper<Customer> query = call.getArgument(0);
            String sql = query.getSqlSegment();
            assertTrue(sql.contains("deleted =") && sql.contains("name =") && sql.contains("work_unit ="));
            assertFalse(sql.contains("LIKE"));
            assertTrue(query.getParamNameValuePairs().containsValue("同名"));
            assertTrue(query.getParamNameValuePairs().containsValue("单位"));
            return List.of(activeCustomer(8));
        });
        assertEquals(8L, customerService.findDuplicates(" 同名 ", " 单位 ").get(0).getId());
    }

    @Test
    void appliesUnitTypeAndPresenceBeforePaging() {
        when(customers.selectPage(any(Page.class), any())).thenAnswer(call -> {
            LambdaQueryWrapper<Customer> query = call.getArgument(1);
            String sql = query.getSqlSegment();
            assertTrue(sql.contains("work_unit LIKE"));
            assertTrue(sql.contains("r.relation_type_id ="));
            assertTrue(sql.contains("NOT EXISTS") && sql.contains("peer.deleted = 0"));
            assertTrue(query.getParamNameValuePairs().containsValue(3L));
            return call.getArgument(0);
        });
        assertEquals(2, customerService.pageList(null, 0, "单位", 3L, false, 2, 10).getCurrent());
    }

    @Test
    void identitySearchOnlyMatchesNameAndUnit() {
        when(customers.selectPage(any(Page.class), any())).thenAnswer(call -> {
            LambdaQueryWrapper<Customer> query = call.getArgument(1);
            String sql = query.getSqlSegment();
            assertTrue(sql.contains("deleted =") && sql.contains("name LIKE") && sql.contains("work_unit LIKE"));
            assertFalse(sql.contains("work_position") || sql.contains("preferences"));
            Page<Customer> page = call.getArgument(0);
            return page.setRecords(List.of(activeCustomer(900)));
        });
        assertEquals(900, relations.searchTargets(null, "单位", true, 1, 10).getRecords().get(0).getId());
    }

    @Test
    void updatesOnlyScopedEmploymentAndAcceptsEmptyMonths() {
        when(customers.selectById(1L)).thenReturn(activeCustomer(1));
        when(history.update(any(), any())).thenAnswer(call -> {
            CustomerEmployment record = call.getArgument(0);
            LambdaQueryWrapper<CustomerEmployment> scope = call.getArgument(1);
            assertEquals("旧单位", record.getWorkUnit());
            assertNull(record.getStartMonth());
            assertNull(record.getEndMonth());
            assertTrue(scope.getSqlSegment().contains("customer_id ="));
            assertTrue(scope.getParamNameValuePairs().containsValue(1L));
            assertTrue(scope.getParamNameValuePairs().containsValue(6L));
            return 1;
        });
        assertEquals(6L, employment.save(1L, 6L, new CustomerEmploymentForm(" 旧单位 ", "职务", " ", null)).getId());
        verify(customers, never()).update(any(), any());
    }

    @Test
    void validatesDatesAndRejectsInactiveOrUnownedRecords() {
        when(customers.selectById(1L)).thenReturn(activeCustomer(1));
        assertThrows(BusinessException.class,
                () -> employment.save(1L, null, new CustomerEmploymentForm("单位", "职务", "2024-13", null)));
        assertThrows(BusinessException.class,
                () -> employment.save(1L, null, new CustomerEmploymentForm("单位", "职务", "2025-01", "2024-01")));
        assertThrows(BusinessException.class, () -> employment.list(2L));
        verifyNoInteractions(history);
        assertThrows(BusinessException.class, () -> employment.delete(1L, 9L));
    }

    @Test
    void protectsEmploymentWritesAndDuplicateCheckWithExistingCustomerPermission() {
        CustomerEmploymentService employmentService = mock(CustomerEmploymentService.class);
        CustomerService service = mock(CustomerService.class);
        CustomerController controller = new CustomerController(service, employmentService, relations, null, null, null);
        AuthUser reader = mock(AuthUser.class);

        controller.employment(1L, reader);
        verify(employmentService).list(1L);
        assertThrows(BusinessException.class, () -> controller.employment(1L, null));
        assertThrows(BusinessException.class, () -> controller.duplicates("姓名", "单位", reader));
        assertThrows(BusinessException.class, () -> controller.createEmployment(1L, null, reader, null));
        assertThrows(BusinessException.class, () -> controller.updateEmployment(1L, 9L, null, reader, null));
        assertThrows(BusinessException.class, () -> controller.deleteEmployment(1L, 9L, reader, null));
        verifyNoInteractions(service);
        verifyNoMoreInteractions(employmentService);
    }

    private static Customer activeCustomer(long id) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setDeleted(0);
        return customer;
    }
}
