package com.eastwind.ElevatorACAfterSaleManager.util;

import javax.crypto.Cipher;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class RSAUtil {
    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(1024);
        return keyPairGen.generateKeyPair();
    }

    public static String encodeKeyToString(Key key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static PublicKey getPublicKey(String key) throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] keyBytes = Base64.getDecoder().decode(key);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(keySpec);
    }

    public static PrivateKey getPrivateKey(String key) throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] keyBytes = Base64.getDecoder().decode(key);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }

    public static String encryptByPublic(String source, String publicKeyString) {
        try {
            RSAPublicKey pubKey = (RSAPublicKey) getPublicKey(publicKeyString);
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, pubKey);
            return Base64.getEncoder().encodeToString(cipher.doFinal(source.getBytes("UTF-8")));
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }
    public static String encryptByPrivate(String source, String privateKeyString) {
        try {
            RSAPrivateKey pubKey = (RSAPrivateKey) getPrivateKey(privateKeyString);
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, pubKey);
            return Base64.getEncoder().encodeToString(cipher.doFinal(source.getBytes("UTF-8")));
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }

    public static String decryptByPublic(String source, String publicKeyString) {
        try {
            RSAPublicKey pubKey = (RSAPublicKey) getPublicKey(publicKeyString);
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, pubKey);
            return Base64.getEncoder().encodeToString(cipher.doFinal(source.getBytes("UTF-8")));
        } catch (Exception e) {
            throw new RuntimeException("解密失败", e);
        }
    }
    public static String decryptByPrivate(String source, String privateKeyString) {
        try {
            RSAPrivateKey pubKey = (RSAPrivateKey) getPrivateKey(privateKeyString);
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, pubKey);
            return Base64.getEncoder().encodeToString(cipher.doFinal(source.getBytes("UTF-8")));
        } catch (Exception e) {
            throw new RuntimeException("解密失败", e);
        }
    }

}
