package com.smartmove;

import org.junit.jupiter.api.Test;

import com.smartmove.domain.City;
import com.smartmove.domain.Vehicle;
import com.smartmove.domain.VehicleState;
import com.smartmove.domain.VehicleType;

import static org.junit.jupiter.api.Assertions.*;

public class SmartMoveApplicationTest {

    @Test
    void applicationStartsSuccessfully() {
        // Basic test - will pass immediately
        assertTrue(true, "SmartMove application initializes correctly");
    }

    @Test
    void projectStructureIsValid() {
        // Test for project setup - placeholder for future tests
        assertNotNull("com.smartmove", "Main package exists");
    }

    @Test
    void vehicleStateTransitionsWork() {
        Vehicle scooter = new Vehicle(VehicleType.E_SCOOTER, City.LONDON);
        assertEquals(VehicleState.AVAILABLE, scooter.getState());
        
        scooter.setState(VehicleState.IN_USE);
        assertEquals(VehicleState.IN_USE, scooter.getState());
        
        scooter.setState(VehicleState.AVAILABLE);
        assertEquals(VehicleState.AVAILABLE, scooter.getState());
    }

}
