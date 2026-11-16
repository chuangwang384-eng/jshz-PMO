package com.jshz.expert.service;

import com.jshz.expert.entity.Expert;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 档案完整性口径：照片+身份证正反面+银行卡+联系方式齐全=完整（Owner 2026-11-07）。 */
class ExpertProfileCompleteTest {
    private Expert expert(String photo, String front, String back, String bank, String contact) {
        Expert e = new Expert();
        e.setPhoto(photo);
        e.setIdCardFront(front);
        e.setIdCardBack(back);
        e.setBankCard(bank);
        e.setContact(contact);
        return e;
    }

    @Test void completeWhenAllFiveFieldsPresent() {
        assertTrue(ExpertService.isProfileComplete(expert("p1.jpg", "f.jpg", "b.jpg", "6222...", "13800000000")));
    }

    @Test void incompleteWhenAnyFieldMissing() {
        assertFalse(ExpertService.isProfileComplete(expert(null, "f.jpg", "b.jpg", "6222...", "138")));
        assertFalse(ExpertService.isProfileComplete(expert("p1.jpg", "", "b.jpg", "6222...", "138")));
        assertFalse(ExpertService.isProfileComplete(expert("p1.jpg", "f.jpg", "b.jpg", "  ", "138")));
        assertFalse(ExpertService.isProfileComplete(expert("p1.jpg", "f.jpg", "b.jpg", null, null)));
        assertFalse(ExpertService.isProfileComplete(null));
    }
}
