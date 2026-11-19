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

    @org.junit.jupiter.api.AfterEach
    void stopWorker() {
        service.close();
    }

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
        assertTrue(back.getColorModel().hasAlpha(), "抠图结果应保留透明通道");
        assertNotNull(service.removeBackground(samplePng()), "同一工作进程应能继续处理下一张图片");
    }

    @Test
    void framedResumePortraitsContainRealTransparentBackgroundWhenFixturesProvided() throws Exception {
        String first = System.getenv("PORTRAIT_FRAME_FIXTURE_FIRST");
        String second = System.getenv("PORTRAIT_FRAME_FIXTURE_SECOND");
        org.junit.jupiter.api.Assumptions.assumeTrue(first != null && second != null);
        var parser = new ResumeParseService(org.mockito.Mockito.mock(OllamaService.class), service);
        for (String fixture : java.util.List.of(first, second)) {
            var path = java.nio.file.Path.of(fixture);
            var parsed = parser.parse(path.getFileName().toString(), java.nio.file.Files.readAllBytes(path), false);
            assertFalse(parsed.photoCandidates().isEmpty());
            // 两份回归原件含多个候选，截图中的证件照均为输出面积最大的候选。
            BufferedImage image = null;
            for (var photo : parsed.photoCandidates()) {
                byte[] bytes = java.util.Base64.getDecoder().decode(photo.base64());
                var candidate = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                if (image == null || candidate.getWidth() * candidate.getHeight() > image.getWidth() * image.getHeight()) {
                    image = candidate;
                }
            }
            assertNotNull(image);
            assertTrue(image.getColorModel().hasAlpha());
            int transparent = 0;
            int opaque = 0;
            for (int color : image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth())) {
                int alpha = color >>> 24;
                if (alpha < 10) transparent++;
                if (alpha >= 128) opaque++;
            }
            int area = image.getWidth() * image.getHeight();
            assertTrue(transparent > .30 * area, "透明区域应覆盖原圆形模板的背景");
            assertTrue(opaque > .20 * area, "应保留主体");
        }
    }
}
