package pers.eastwind.billmanager.user.util;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import pers.eastwind.billmanager.common.exception.BizException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * JWT 工具类
 */
public class JWTUtil {


    /**
     * 生成 TOKEN
     *
     * @param secret 密钥
     * @param userName 用户名
     * @param subject  摘要
     * @return TOKEN
     */
    public static String generateToken(String secret, long expireSecond, String userName, String subject) {
        Instant now = Instant.now();
        Algorithm algorithm = Algorithm.HMAC256(secret);
        try {
            return JWT.create()
                    .withAudience(userName)
                    .withSubject(subject)
                    .withIssuedAt(now.plus(expireSecond, ChronoUnit.SECONDS))
                    .sign(algorithm);
        } catch (JWTCreationException e) {
            throw new BizException("JWT 签名失败", e);
        }
    }

    /**
     * 验证 TOKEN
     */
    public static DecodedJWT verifyToken(String secret, String token) {
        Algorithm algorithm = Algorithm.HMAC256(secret);
        try {
            return JWT.require(algorithm).build().verify(token);
        } catch (JWTVerificationException e) {
            throw new BizException("验证 JWT 异常", e);
        }
    }

}
