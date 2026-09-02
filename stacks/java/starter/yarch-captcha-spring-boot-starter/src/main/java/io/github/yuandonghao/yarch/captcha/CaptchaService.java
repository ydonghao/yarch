package io.github.yuandonghao.yarch.captcha;

import io.github.yuandonghao.yarch.redis.RedisKeys;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 图形验证码（E2）：生成 4 位去混淆字符 + 噪线；答案进 Redis（key 首段=服务名， TTL 120s、一次性——verify 即删）。 */
public class CaptchaService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final String serviceName;

    public CaptchaService(StringRedisTemplate redis, String serviceName) {
        this.redis = redis;
        this.serviceName = serviceName;
    }

    public record Captcha(String key, String imageBase64) {}

    public Captcha generate() {
        StringBuilder answer = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            answer.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        String key = UUID.randomUUID().toString();
        redis.opsForValue().set(storeKey(key), answer.toString(), Duration.ofSeconds(120));
        return new Captcha(key, renderPng(answer.toString()));
    }

    /** 一次性校验：对/错都消费该 key（防重放枚举） */
    public boolean verify(String key, String answer) {
        String stored = redis.opsForValue().get(storeKey(key));
        redis.delete(storeKey(key));
        return stored != null && stored.equalsIgnoreCase(answer == null ? "" : answer.trim());
    }

    String peek(String key) {
        return redis.opsForValue().get(storeKey(key));
    }

    long ttlSeconds(String key) {
        Long ttl = redis.getExpire(storeKey(key));
        return ttl == null ? -1 : ttl;
    }

    private String storeKey(String key) {
        return RedisKeys.of(serviceName).parts("captcha", key);
    }

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
