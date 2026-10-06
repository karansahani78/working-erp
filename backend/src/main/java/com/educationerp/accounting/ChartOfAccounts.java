package com.educationerp.accounting;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A node in the institution's chart of accounts. Only postable accounts may appear on a
 * journal line, so a group heading can never be debited directly.
 */
@Entity
@Table(name = "chart_of_accounts")
public class ChartOfAccounts extends BaseEntity {

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_group", nullable = false, length = 30)
    private AccountGroup accountGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "is_postable", nullable = false)
    private boolean postable = true;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public enum AccountGroup {
        ASSET, LIABILITY, EQUITY, INCOME, EXPENSE
    }

    public enum AccountType {
        CURRENT_ASSET, FIXED_ASSET, RECEIVABLE, CASH, BANK,
        CURRENT_LIABILITY, PAYABLE,
        CAPITAL, REVENUE,
        OPERATING_EXPENSE, ADMIN_EXPENSE, FINANCIAL_EXPENSE,
        INCOME, OTHER
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AccountGroup getAccountGroup() {
        return accountGroup;
    }

    public void setAccountGroup(AccountGroup accountGroup) {
        this.accountGroup = accountGroup;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public void setAccountType(AccountType accountType) {
        this.accountType = accountType;
    }

    public UUID getParentId() {
        return parentId;
    }

    public void setParentId(UUID parentId) {
        this.parentId = parentId;
    }

    public boolean isPostable() {
        return postable;
    }

    public void setPostable(boolean postable) {
        this.postable = postable;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
