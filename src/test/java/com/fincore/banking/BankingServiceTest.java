package com.fincore.banking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankingServiceTest {
    @Mock JdbcTemplate jdbc;
    @InjectMocks BankingService service;

    @Test void rejectsZeroOrNegativeAmountsBeforeWriting() {
        assertThrows(IllegalArgumentException.class, () -> service.deposit(7, 42, new BigDecimal("0.00"), "test"));
        assertThrows(IllegalArgumentException.class, () -> service.withdraw(7, 42, new BigDecimal("-1.00"), "test"));
        verifyNoInteractions(jdbc);
    }

    @Test void blocksWithdrawalWhenFundsAreInsufficient() {
        var account = new BankingService.Account(42, 7, "FC0000000042", "INR", new BigDecimal("10.00"), "ACTIVE");
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(account));
        assertThrows(IllegalStateException.class, () -> service.withdraw(7, 42, new BigDecimal("10.01"), "Rent"));
        verify(jdbc, never()).update(contains("UPDATE accounts"), any(), any());
    }

    @Test void recordsDepositAndUpdatedBalance() {
        var account = new BankingService.Account(42, 7, "FC0000000042", "INR", new BigDecimal("10.00"), "ACTIVE");
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(account));
        var result = service.deposit(7, 42, new BigDecimal("5.25"), "Payday");
        assertEquals(new BigDecimal("15.25"), result.get("balance"));
        assertEquals("DEPOSIT", result.get("type"));
        verify(jdbc).update(contains("UPDATE accounts SET balance"), eq(new BigDecimal("15.25")), eq(42L));
        verify(jdbc).update(contains("INSERT INTO transactions"), any(), eq(42L), eq("DEPOSIT"), eq(new BigDecimal("5.25")), eq(new BigDecimal("15.25")), eq("Payday"));
    }
}
