package com.fincore.banking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api")
public class BankingController {
    private final BankingService service;
    public BankingController(BankingService service) { this.service=service; }
    record AmountRequest(@DecimalMin(value="0.01") @Digits(integer=17,fraction=2) BigDecimal amount, @Size(max=180) String description) {}
    record TransferRequest(long fromAccountId,@NotBlank @Pattern(regexp="FC\\d{10}") String toAccountNumber,@DecimalMin(value="0.01") @Digits(integer=17,fraction=2) BigDecimal amount,@Size(max=180) String description) {}
    record AccountRequest(@NotBlank String accountType) {}
    private long customer(JwtAuthenticationToken auth) { return Long.parseLong(auth.getToken().getSubject()); }
    @GetMapping("/dashboard") Map<String,Object> dashboard(JwtAuthenticationToken auth) { return service.summary(customer(auth)); }
    @GetMapping("/accounts") List<Map<String,Object>> accounts(JwtAuthenticationToken auth) { return service.accounts(customer(auth)); }
    @PostMapping("/accounts") Map<String,Object> createAccount(JwtAuthenticationToken auth,@Valid @RequestBody AccountRequest r) { return service.createAccount(customer(auth),r.accountType().toUpperCase()); }
    @GetMapping("/accounts/{id}/transactions") List<Map<String,Object>> transactions(JwtAuthenticationToken auth,@PathVariable long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { if(page<0||size<1||size>100)throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and 100"); return service.transactions(customer(auth),id,page,size); }
    @PostMapping("/accounts/{id}/deposit") Map<String,Object> deposit(JwtAuthenticationToken auth,@PathVariable long id,@Valid @RequestBody AmountRequest r) { return service.deposit(customer(auth),id,r.amount(),r.description()); }
    @PostMapping("/accounts/{id}/withdraw") Map<String,Object> withdraw(JwtAuthenticationToken auth,@PathVariable long id,@Valid @RequestBody AmountRequest r) { return service.withdraw(customer(auth),id,r.amount(),r.description()); }
    @PostMapping("/transfers") Map<String,Object> transfer(JwtAuthenticationToken auth,@Valid @RequestBody TransferRequest r) { return service.transfer(customer(auth),r.fromAccountId(),r.toAccountNumber(),r.amount(),r.description()); }
}
