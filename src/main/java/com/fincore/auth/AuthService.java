package com.fincore.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import java.time.Instant;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.security.SecureRandom;

@Service
public class AuthService {
    private final JdbcTemplate jdbc; private final PasswordEncoder passwords; private final JwtEncoder jwt; private final Duration expiration; private final SecureRandom random = new SecureRandom();
    public AuthService(JdbcTemplate jdbc, PasswordEncoder passwords, JwtEncoder jwt, @Value("${app.jwt.expiration}") Duration expiration) { this.jdbc=jdbc; this.passwords=passwords; this.jwt=jwt; this.expiration=expiration; }
    @Transactional public Map<String,Object> register(String name, String email, String password) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM customers WHERE email = ?", Integer.class, normalized) > 0) throw new IllegalStateException("An account with this email already exists");
        jdbc.update("INSERT INTO customers(full_name,email,password_hash) VALUES(?,?,?)", name.trim(), normalized, passwords.encode(password));
        long id = jdbc.queryForObject("SELECT id FROM customers WHERE email = ?", Long.class, normalized);
        jdbc.update("INSERT INTO accounts(customer_id,account_number,account_type,balance,currency) VALUES(?,?, 'CHECKING',0.00,'INR')", id, newAccountNumber());
        return session(id, name.trim(), normalized);
    }
    public Map<String,Object> login(String email, String password) {
        var rows = jdbc.query("SELECT id,full_name,email,password_hash FROM customers WHERE email = ?", (rs,n) -> new Object[]{rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4)}, email.trim().toLowerCase(Locale.ROOT));
        if (rows.isEmpty() || !passwords.matches(password, (String) rows.get(0)[3])) throw new IllegalArgumentException("Email or password is incorrect");
        return session((Long)rows.get(0)[0], (String)rows.get(0)[1], (String)rows.get(0)[2]);
    }
    private Map<String,Object> session(long id, String name, String email) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer("https://fincore.local")
            .issuedAt(now)
            .expiresAt(now.plus(expiration))
            .subject(Long.toString(id))
            .claim("name", name)
            .claim("email", email)
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwt.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return Map.of("token",token,"customer",Map.of("id",id,"name",name,"email",email));
    }
    private String newAccountNumber() { StringBuilder s=new StringBuilder("FC"); for(int i=0;i<10;i++) s.append(random.nextInt(10)); return s.toString(); }
}
