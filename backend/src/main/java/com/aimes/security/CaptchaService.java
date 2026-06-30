package com.aimes.security;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.core.util.IdUtil;
import com.aimes.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.awt.Color;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate stringRedisTemplate;

    public Map<String, Object> create() {
        String id = IdUtil.simpleUUID();
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(130, 48, 4, 50);
        captcha.setBackground(Color.WHITE);
        stringRedisTemplate.opsForValue().set(key(id), captcha.getCode().toLowerCase(), TTL);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("img", captcha.getImageBase64());
        return result;
    }

    public void verify(String id, String answer) {
        if (!StringUtils.hasText(id) || !StringUtils.hasText(answer)) {
            throw new BusinessException("请完成验证码");
        }
        String expected = stringRedisTemplate.opsForValue().get(key(id));
        stringRedisTemplate.delete(key(id));
        if (!StringUtils.hasText(expected)) {
            throw new BusinessException("验证码已过期，请刷新");
        }
        if (!expected.trim().equalsIgnoreCase(answer.trim())) {
            throw new BusinessException("验证码错误");
        }
    }

    private String key(String id) {
        return "captcha:" + id;
    }
}
