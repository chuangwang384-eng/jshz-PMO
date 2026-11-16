package com.jshz.expert.service;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.Supplier;
import com.jshz.expert.mapper.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class SupplierDeletionRegressionTest {
    @InjectMocks SupplierService service;
    @Mock SupplierMapper suppliers;
    @Mock ProjectSupplierMapper projectSuppliers;
    @Mock SupplierImageService images;
    @Mock SupplierPriceItemMapper prices;
    @Mock SupplierReviewMapper reviews;
    @Mock SupplierHotelPrintRelMapper relations;
    @Test void referencedSupplierIsRejectedBeforeAnyDeletion() {
        ReflectionTestUtils.setField(service,"baseMapper",suppliers);
        when(suppliers.selectOne(any())).thenReturn(new Supplier());
        when(projectSuppliers.selectCount(any())).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.deleteSupplier(1L));
        verify(suppliers,never()).deleteById(anyLong()); verifyNoInteractions(images,prices,reviews,relations);
    }
}
