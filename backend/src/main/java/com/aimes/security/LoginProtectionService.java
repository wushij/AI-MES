package com.aimes.security;

import com.aimes.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class LoginProtectionService {

    private static final long CAPTCHA_AFTER_FAILURES = 3;
    private static final long USER_LOCK_THRESHOLD = 5;
    private static final long IP_BAN_THRESHOLD = 30;
    private static final Duration USER_LOCK_TTL = Duration.ofMinutes(15);
    private static final Duration IP_BAN_TTL = Duration.ofMinutes(60);

    private final StringRedisTemplate stringRedisTemplate;

    public void checkAllowed(String ip, String username) {
        if (isLocked("login:lock:ip:" + ip)) {
            throw new BusinessException("登录失败次数过多，请 60 分钟后再试");
        }
        if (StringUtils.hasText(username) && isLocked("login:lock:user:" + username.toLowerCase())) {
            throw new BusinessException("账号已临时锁定，请 15 分钟后再试");
        }
    }

    public boolean captchaRequired(String ip) {
        return readCount("login:fail:ip:" + ip) >= CAPTCHA_AFTER_FAILURES;
    }

    public void recordFailure(String ip, String username) {
        long ipFails = increment("login:fail:ip:" + ip, IP_BAN_TTL);
        if (ipFails >= IP_BAN_THRESHOLD) {
            lock("login:lock:ip:" + ip, IP_BAN_TTL);
        }

        if (StringUtils.hasText(username)) {
            String userKey = "login:fail:user:" + username.toLowerCase();
            long userFails = increment(userKey, USER_LOCK_TTL);
            if (userFails >= USER_LOCK_THRESHOLD) {
                lock("login:lock:user:" + username.toLowerCase(), USER_LOCK_TTL);
            }
        }
    }

    public void clearOnSuccess(String ip, String username) {
        stringRedisTemplate.delete("login:fail:ip:" + ip);
        stringRedisTemplate.delete("login:lock:ip:" + ip);
        if (StringUtils.hasText(username)) {
            stringRedisTemplate.delete("login:fail:user:" + username.toLowerCase());
            stringRedisTemplate.delete("login:lock:user:" + username.toLowerCase());
        }
    }

    private boolean isLocked(String key) {
        return "1".equals(stringRedisTemplate.opsForValue().get(key));
    }

    private void lock(String key, Duration ttl) {
        stringRedisTemplate.opsForValue().set(key, "1", ttl);
    }

    private long increment(String key, Duration ttl) {
        Long value = stringRedisTemplate.opsForValue().increment(key);
        if (value != null && value == 1) {
            stringRedisTemplate.expire(key, ttl);
        }
        return value == null ? 0L : value;
    }

    private long readCount(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(value)) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}
