package com.smartmove.storage;

import com.smartmove.domain.Vehicle;
import java.util.List;
import java.util.Optional;

public interface VehicleStorage {
    Optional<Vehicle> findById(String id);
    List<Vehicle> findAll();
    void save(Vehicle vehicle);
    void deleteById(String id);
}
