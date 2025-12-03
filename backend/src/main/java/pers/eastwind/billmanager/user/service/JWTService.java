package pers.eastwind.billmanager.user.service;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.common.exception.BizException;

import java.text.ParseException;
import java.util.Date;
import java.util.Objects;

/**
 * JWT 工具类
 */
@Service
public class JWTService {
    private final String jwtKey;
    private JWSSigner signer;
    private JWSVerifier verifier;

    public JWTService(@Value("${config.jwt.key}") String jwtKey) {
        this.jwtKey = jwtKey;
    }

    @PostConstruct
    public void init() throws JOSEException {
        byte[] shareSecurity = jwtKey.getBytes();
        signer = new MACSigner(shareSecurity);
        verifier = new MACVerifier(shareSecurity);
    }

    /**
     * 生成 TOKEN
     *
     * @param userName       用户名
     * @param subject        摘要
     * @param expiresSeconds 过期秒数
     * @return TOKEN
     */
    public String generateToken(String userName, String subject, long expiresSeconds) {
        Date now = new Date();
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .audience(userName)
                .subject(subject)
                .issueTime(now)
                // 24 小时后过期
                .expirationTime(new Date(now.getTime() + expiresSeconds * 1000))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new BizException("JWT 签名失败");
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
            throw new BizException("解析 JWT 失败");
        }
        try {
            // 校验签名加密、过期时间、摘要
            return jwt.verify(verifier)
                    && claimsSet.getExpirationTime().after(new Date())
                    && Objects.equals(claimsSet.getSubject(), subject);
        } catch (JOSEException e) {
            throw new BizException("验证 JWT 异常");
        }
    }
}
