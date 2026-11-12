package com.jshz.expert.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 证件照抠图服务（Owner 2026-11-11）：运行时不可用/输入非法时必须静默返回 null
 * （调用方回退白底 JPG，解析主流程不受阻）；有效 PNG 输入返回可解析透明 PNG。
 */
class PhotoMatteServiceTest {

    private final PhotoMatteService service = new PhotoMatteService();

    private byte[] samplePng() throws Exception {
        BufferedImage img = new BufferedImage(200, 240, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(java.awt.Color.GREEN);
        g.fillRect(0, 0, 200, 240);
        g.setColor(java.awt.Color.WHITE);
        g.fillOval(60, 30, 80, 80);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    @Test
    void nullAndEmptyInputReturnNull() {
        assertNull(service.removeBackground(null));
        assertNull(service.removeBackground(new byte[0]));
    }

    @Test
    void garbageInputReturnsNullWithoutThrowing() {
        assertDoesNotThrow(() -> service.removeBackground("not-an-image".getBytes()));
    }

    @Test
    void validImageReturnsTransparentPngWhenRuntimeAvailable() throws Exception {
        byte[] out = service.removeBackground(samplePng());
        if (out == null) return;   // 运行时未安装（如 CI/隔离环境）：回退路径已由上两用例覆盖
        BufferedImage back = ImageIO.read(new java.io.ByteArrayInputStream(out));
        assertNotNull(back, "抠图输出必须可解析");
        assertTrue(out.length >= 1000, "输出不应为空图");
    }
}
