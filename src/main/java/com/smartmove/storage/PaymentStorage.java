package com.smartmove.storage;

import com.smartmove.domain.Payment;
import java.util.List;

public interface PaymentStorage {
    void save(Payment payment);
    List<Payment> findAll();
}
