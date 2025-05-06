package com.eastwind.EACAfterSaleMgr.util;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.text.ParseException;
import java.util.Date;

/**
 * JWT 工具类
 */
@Component
public class JWTUtil {
    private final JWSSigner signer;
    private final JWSVerifier verifier;

    private JWTUtil() throws JOSEException {
        byte[] shareSecurity = new byte[32];
        SecureRandom random = new SecureRandom();
        random.nextBytes(shareSecurity);

        signer = new MACSigner(shareSecurity);
        verifier = new MACVerifier(shareSecurity);
    }

    /**
     * 生成 TOKEN
     *
     * @param userName 用户名
     * @return TOKEN
     */
    public String generateToken(String userName, String origin) {
        Date now = new Date();
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .audience(userName)
                .subject(origin)
                .issueTime(now)
                // 24 小时后过期
                .expirationTime(new Date(now.getTime() + 24 * 60 * 60 * 1000))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new RuntimeException("JWT 签名失败");
        }
        return jwt.serialize();
    }

    /**
     * 验证 TOKEN
     */
    public boolean verifyToken(String token) {
        SignedJWT jwt = null;
        try {
            jwt = SignedJWT.parse(token);
        } catch (ParseException e) {
            throw new RuntimeException("解析 JWT 失败");
        }
        try {
            return jwt.verify(verifier);
        } catch (JOSEException e) {
            throw new RuntimeException("验证 JWT 异常");
        }
    }
}
