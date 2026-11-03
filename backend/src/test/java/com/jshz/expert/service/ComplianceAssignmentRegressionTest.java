package com.jshz.expert.service;
import com.jshz.expert.entity.*;
import com.jshz.expert.mapper.*;
import com.jshz.expert.security.AuthUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class ComplianceAssignmentRegressionTest {
    @InjectMocks ComplianceService service;
    @Mock ComplianceItemMapper items;
    @Mock ComplianceItemAssigneeMapper assignees;
    @Mock ComplianceItemHistoryMapper histories;
    @Mock ComplianceUserProgressMapper progresses;
    @Mock NotificationService notifications;
    @Test void editingAIntoABNotifiesOnlyBOnce() {
        ComplianceItem item = new ComplianceItem(); item.setId(1L); item.setTitle("fixture"); item.setStatus("PENDING");
        ComplianceItemAssignee a = new ComplianceItemAssignee(); a.setUserId(2L);
        ComplianceItemAssignee b = new ComplianceItemAssignee(); b.setUserId(3L);
        when(items.selectById(1L)).thenReturn(item);
        when(assignees.selectList(any())).thenReturn(List.of(a),List.of(a,b));
        service.updateItem(1L,Map.of("assigneeIds",List.of(2L,3L)),new AuthUser(4L,"fixture",true,false,Set.of(),Set.of()));
        verify(notifications).notify(eq(3L),anyString(),anyString(),anyString(),anyString());
        verify(notifications,never()).notify(eq(2L),anyString(),anyString(),anyString(),anyString());
        verifyNoMoreInteractions(notifications);
    }
}
