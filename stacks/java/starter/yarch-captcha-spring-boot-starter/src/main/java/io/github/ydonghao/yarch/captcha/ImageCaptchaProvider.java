package io.github.ydonghao.yarch.captcha;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import javax.imageio.ImageIO;

/** image 档（contract/api/captcha.md 二-2，LOCAL 默认档）：4 位去混淆字符集【强制】；渲染样式自由（CP9）。 */
public class ImageCaptchaProvider implements CaptchaProvider {

    /** 去混淆 32 字符集（二-2：去 I/O/0/1，跨栈 conformance 向量 V 系断言对象） */
    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String id() {
        return "image";
    }

    @Override
    public VerifyMode mode() {
        return VerifyMode.LOCAL;
    }

    @Override
    public IssuedChallenge issue(IssueRequest request) {
        StringBuilder answer = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            answer.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return new IssuedChallenge(
                answer.toString(), Map.of("imageBase64", renderPng(answer.toString())));
    }

    // matches 用默认口径：trim + 大小写不敏感（二-2）

    private String renderPng(String text) {
        try {
            BufferedImage image = new BufferedImage(150, 50, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 150, 50);
            for (int i = 0; i < 6; i++) {
                g.setColor(
                        new Color(RANDOM.nextInt(200), RANDOM.nextInt(200), RANDOM.nextInt(200)));
                g.setStroke(new BasicStroke(1.4f));
                g.drawLine(
                        RANDOM.nextInt(150),
                        RANDOM.nextInt(50),
                        RANDOM.nextInt(150),
                        RANDOM.nextInt(50));
            }
            g.setFont(new Font("SansSerif", Font.BOLD, 30));
            for (int i = 0; i < text.length(); i++) {
                g.setColor(
                        new Color(
                                20 + RANDOM.nextInt(100),
                                20 + RANDOM.nextInt(100),
                                20 + RANDOM.nextInt(100)));
                g.drawString(String.valueOf(text.charAt(i)), 15 + i * 32, 36);
            }
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("captcha render failed", e);
        }
    }
}
