package pers.eastwind.billmanager.service;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.config.ConfigProperties;

import java.text.ParseException;
import java.util.Date;
import java.util.Objects;

/**
 * JWT 工具类
 */
@Service
public class JWTService {
    private final ConfigProperties properties;
    private JWSSigner signer;
    private JWSVerifier verifier;

    public JWTService(ConfigProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() throws JOSEException {
        byte[] shareSecurity = properties.getJwt().getKey().getBytes();
        signer = new MACSigner(shareSecurity);
        verifier = new MACVerifier(shareSecurity);
    }

    /**
     * 生成 TOKEN
     *
     * @param userName 用户名
     * @return TOKEN
     */
    public String generateToken(String userName, String subject) {
        Date now = new Date();
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .audience(userName)
                .subject(subject)
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
    public boolean verifyToken(String token, String subject) {
        SignedJWT jwt;
        JWTClaimsSet claimsSet;
        try {
            jwt = SignedJWT.parse(token);
            claimsSet = jwt.getJWTClaimsSet();
        } catch (ParseException e) {
            throw new RuntimeException("解析 JWT 失败");
        }
        try {
            // 校验签名加密、过期时间、摘要
            return jwt.verify(verifier)
                    && claimsSet.getExpirationTime().after(new Date())
                    && Objects.equals(claimsSet.getSubject(), subject);
        } catch (JOSEException e) {
            throw new RuntimeException("验证 JWT 异常");
        }
    }
}
