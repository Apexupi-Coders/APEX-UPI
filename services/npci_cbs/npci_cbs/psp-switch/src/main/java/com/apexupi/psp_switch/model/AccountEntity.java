package com.apexupi.psp_switch.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "accounts")
public class AccountEntity {
    
    @Id
    private String upiId;
    
    private double balance = 0.0;
    
    // Methods for simulation
    public boolean debit(double amount) {
        if (balance >= amount) {
            balance -= amount;
            return true;
        }
        return false;
    }
    
    public void credit(double amount) {
        balance += amount;
    }
}

