package com.flux.user_management_service.security;

import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Encoders;

public class JwtKeyGen {
    public static void main(String[] args) {
        String key = Encoders.BASE64.encode(
                Keys.secretKeyFor(SignatureAlgorithm.HS512).getEncoded()
        );
        System.out.println(key);
    }
}
