package com.fincore.banking;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.security.SecureRandom;

@Service
public class BankingService {
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();
    public BankingService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public List<Map<String,Object>> accounts(long customerId) {
        return jdbc.queryForList("SELECT id, account_number AS accountNumber, account_type AS accountType, balance, currency, status, created_at AS createdAt FROM accounts WHERE customer_id = ? ORDER BY created_at DESC", customerId);
    }

    @Transactional public Map<String,Object> createAccount(long customerId, String accountType) {
        if (!List.of("CHECKING", "SAVINGS").contains(accountType)) throw new IllegalArgumentException("Account type must be CHECKING or SAVINGS");
        String number; do { number="FC"+String.format("%010d", random.nextLong(10_000_000_000L)); } while (jdbc.queryForObject("SELECT COUNT(*) FROM accounts WHERE account_number = ?", Integer.class, number)>0);
        jdbc.update("INSERT INTO accounts(customer_id,account_number,account_type,balance,currency) VALUES(?,?,?,0.00,'INR')",customerId,number,accountType);
        return jdbc.queryForMap("SELECT id,account_number AS accountNumber,account_type AS accountType,balance,currency,status,created_at AS createdAt FROM accounts WHERE account_number = ?",number);
    }

    public Map<String,Object> summary(long customerId) {
        List<Map<String,Object>> accounts=accounts(customerId);
        BigDecimal total=accounts.stream().map(a -> (BigDecimal)a.get("balance")).reduce(BigDecimal.ZERO,BigDecimal::add);
        List<Map<String,Object>> recent=jdbc.queryForList("SELECT t.id,t.reference,t.transaction_type AS type,t.amount,t.description,t.created_at AS createdAt,t.account_id AS accountId,a.account_number AS accountNumber FROM transactions t JOIN accounts a ON a.id=t.account_id WHERE a.customer_id=? ORDER BY t.created_at DESC,t.id DESC LIMIT 6",customerId);
        Map<String,Object> monthToDate=jdbc.queryForMap("SELECT COALESCE(SUM(CASE WHEN t.transaction_type='DEPOSIT' OR (t.transaction_type='TRANSFER_IN' AND cp.customer_id<>a.customer_id) THEN t.amount ELSE 0 END),0.00) AS moneyIn, COALESCE(SUM(CASE WHEN t.transaction_type='WITHDRAWAL' OR (t.transaction_type='TRANSFER_OUT' AND cp.customer_id<>a.customer_id) THEN t.amount ELSE 0 END),0.00) AS moneyOut FROM transactions t JOIN accounts a ON a.id=t.account_id LEFT JOIN accounts cp ON cp.id=t.counterparty_account_id WHERE a.customer_id=? AND t.created_at >= DATE_FORMAT(CURRENT_DATE(), '%Y-%m-01')",customerId);
        List<Map<String,Object>> cashFlow=jdbc.queryForList("SELECT DATE_FORMAT(t.created_at,'%Y-%m') AS month, COALESCE(SUM(CASE WHEN t.transaction_type='DEPOSIT' OR (t.transaction_type='TRANSFER_IN' AND cp.customer_id<>a.customer_id) THEN t.amount ELSE 0 END),0.00) AS moneyIn, COALESCE(SUM(CASE WHEN t.transaction_type='WITHDRAWAL' OR (t.transaction_type='TRANSFER_OUT' AND cp.customer_id<>a.customer_id) THEN t.amount ELSE 0 END),0.00) AS moneyOut FROM transactions t JOIN accounts a ON a.id=t.account_id LEFT JOIN accounts cp ON cp.id=t.counterparty_account_id WHERE a.customer_id=? AND t.created_at >= DATE_FORMAT(DATE_SUB(CURRENT_DATE(), INTERVAL 5 MONTH), '%Y-%m-01') GROUP BY DATE_FORMAT(t.created_at,'%Y-%m') ORDER BY month",customerId);
        return Map.of("totalBalance",total,"accountCount",accounts.size(),"currency","INR","accounts",accounts,"recentTransactions",recent,"monthToDate",monthToDate,"cashFlow",cashFlow);
    }

    public List<Map<String,Object>> transactions(long customerId,long accountId,int page,int size) {
        requireOwnedAccount(customerId,accountId);
        int offset=page*size;
        return jdbc.queryForList("SELECT t.id,t.reference,t.transaction_type AS type,t.amount,t.balance_after AS balanceAfter,t.description,t.created_at AS createdAt,t.counterparty_account_id AS counterpartyAccountId,ca.account_number AS counterpartyAccountNumber FROM transactions t LEFT JOIN accounts ca ON ca.id=t.counterparty_account_id WHERE t.account_id=? ORDER BY t.created_at DESC,t.id DESC LIMIT ? OFFSET ?",accountId,size,offset);
    }

    @Transactional public Map<String,Object> deposit(long customerId,long accountId,BigDecimal amount,String description) { return adjust(customerId,accountId,amount,description,"DEPOSIT",true); }
    @Transactional public Map<String,Object> withdraw(long customerId,long accountId,BigDecimal amount,String description) { return adjust(customerId,accountId,amount,description,"WITHDRAWAL",false); }

    @Transactional public Map<String,Object> transfer(long customerId,long sourceId,String recipientNumber,BigDecimal amount,String description) {
        amount=money(amount);
        Long destinationId=jdbc.query("SELECT id FROM accounts WHERE account_number=? AND status='ACTIVE'",rs -> rs.next()?rs.getLong(1):null,recipientNumber.trim());
        if(destinationId==null) throw new IllegalArgumentException("Recipient account was not found");
        if(sourceId==destinationId) throw new IllegalArgumentException("Choose a different recipient account");
        long first=Math.min(sourceId,destinationId),second=Math.max(sourceId,destinationId);
        Account a=lock(first),b=lock(second);
        Account source=sourceId==a.id?a:b, dest=destinationId==a.id?a:b;
        if(source.customerId!=customerId) throw new IllegalArgumentException("Account was not found");
        ensureActive(source); ensureActive(dest);
        if(!source.currency.equals(dest.currency)) throw new IllegalArgumentException("Accounts must use the same currency");
        if(source.balance.compareTo(amount)<0) throw new IllegalStateException("Insufficient funds");
        BigDecimal sourceBalance=source.balance.subtract(amount), destBalance=dest.balance.add(amount);
        jdbc.update("UPDATE accounts SET balance=? WHERE id=?",sourceBalance,source.id);
        jdbc.update("UPDATE accounts SET balance=? WHERE id=?",destBalance,dest.id);
        String ref=UUID.randomUUID().toString(); String memo=cleanDescription(description,"Transfer");
        jdbc.update("INSERT INTO transactions(reference,account_id,counterparty_account_id,transaction_type,amount,balance_after,description) VALUES(?,?,?,'TRANSFER_OUT',?,?,?)",ref,source.id,dest.id,amount,sourceBalance,memo);
        jdbc.update("INSERT INTO transactions(reference,account_id,counterparty_account_id,transaction_type,amount,balance_after,description) VALUES(?,?,?,'TRANSFER_IN',?,?,?)",ref,dest.id,source.id,amount,destBalance,memo);
        return Map.of("reference",ref,"amount",amount,"fromAccountId",source.id,"toAccountNumber",dest.number,"fromBalance",sourceBalance,"toBalance",destBalance,"description",memo);
    }

    private Map<String,Object> adjust(long customerId,long accountId,BigDecimal rawAmount,String description,String type,boolean add) {
        BigDecimal amount=money(rawAmount); Account a=lock(accountId);
        if(a.customerId!=customerId) throw new IllegalArgumentException("Account was not found"); ensureActive(a);
        if(!add&&a.balance.compareTo(amount)<0) throw new IllegalStateException("Insufficient funds");
        BigDecimal balance=add?a.balance.add(amount):a.balance.subtract(amount);
        jdbc.update("UPDATE accounts SET balance=? WHERE id=?",balance,a.id);
        String ref=UUID.randomUUID().toString(); String memo=cleanDescription(description,type.equals("DEPOSIT")?"Cash deposit":"Cash withdrawal");
        jdbc.update("INSERT INTO transactions(reference,account_id,transaction_type,amount,balance_after,description) VALUES(?,?,?,?,?,?)",ref,a.id,type,amount,balance,memo);
        return Map.of("reference",ref,"accountId",a.id,"accountNumber",a.number,"type",type,"amount",amount,"balance",balance,"description",memo);
    }
    private Account lock(long id) {
        List<Account> rows=jdbc.query("SELECT id,customer_id,account_number,currency,balance,status FROM accounts WHERE id=? FOR UPDATE",(rs,n)->new Account(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4),rs.getBigDecimal(5),rs.getString(6)),id);
        if(rows.isEmpty()) throw new IllegalArgumentException("Account was not found"); return rows.get(0);
    }
    private void requireOwnedAccount(long customerId,long accountId) { Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM accounts WHERE id=? AND customer_id=?",Integer.class,accountId,customerId); if(n==null||n==0) throw new IllegalArgumentException("Account was not found"); }
    private void ensureActive(Account a) { if(!"ACTIVE".equals(a.status)) throw new IllegalStateException("Account is not active"); }
    private BigDecimal money(BigDecimal v) { if(v==null||v.scale()>2||v.compareTo(BigDecimal.ZERO)<=0) throw new IllegalArgumentException("Amount must be greater than zero with at most two decimal places"); return v.setScale(2,RoundingMode.UNNECESSARY); }
    private String cleanDescription(String s,String fallback) { String v=s==null?"":s.trim(); if(v.isBlank())return fallback; return v.length()>180?v.substring(0,180):v; }
    record Account(long id,long customerId,String number,String currency,BigDecimal balance,String status) {}
}
