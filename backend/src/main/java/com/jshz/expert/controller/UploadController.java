package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.service.PhotoStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 照片上传接口（正式版 V1.001，仅 ADMIN）。
 * /api/upload/photo 证件照；/api/upload/idcard?side=front|back 身份证正反面。
 * 命名规则：avatar_<id>_<时间戳> / idcard_front_<id>_<时间戳> / idcard_back_<id>_<时间戳>。
 */
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final PhotoStorageService photoStorageService;

    /**
     * 上传证件照：POST /api/upload/photo?expertId=123，文件字段名 photo。
     */
    @PostMapping("/photo")
    public ApiResponse<Map<String, Object>> uploadPhoto(@RequestParam Long expertId,
                                                        @RequestParam("photo") MultipartFile file) {
        String fileName = photoStorageService.savePhoto(file, expertId, "photo");
        return ok(fileName);
    }

    /**
     * 上传身份证正/反面：POST /api/upload/idcard?expertId=123&side=front|back，文件字段名 idcard。
     */
    @PostMapping("/idcard")
    public ApiResponse<Map<String, Object>> uploadIdcard(@RequestParam Long expertId,
                                                         @RequestParam(defaultValue = "front") String side,
                                                         @RequestParam("idcard") MultipartFile file) {
        String type;
        if ("back".equalsIgnoreCase(side)) {
            type = "idcard_back";
        } else if ("front".equalsIgnoreCase(side)) {
            type = "idcard_front";
        } else {
            throw new BusinessException("side 参数仅支持 front / back");
        }
        String fileName = photoStorageService.savePhoto(file, expertId, type);
        return ok(fileName);
    }

    private ApiResponse<Map<String, Object>> ok(String fileName) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fileName", fileName);
        data.put("url", "/api/files/" + fileName);
        return ApiResponse.ok(data);
    }
}
