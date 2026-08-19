package bg.softuni.garage.config;

import bg.softuni.garage.mechanic.Mechanic;
import bg.softuni.garage.mechanic.MechanicRepository;
import bg.softuni.garage.mechanic.Specialty;
import bg.softuni.garage.user.User;
import bg.softuni.garage.user.UserRepository;
import bg.softuni.garage.vehicle.Vehicle;
import bg.softuni.garage.vehicle.VehicleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
@Order(2)
@Slf4j
public class WorkshopDataInitializer implements CommandLineRunner {

    private final MechanicRepository mechanicRepository;
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;

    public WorkshopDataInitializer(MechanicRepository mechanicRepository,
                                   VehicleRepository vehicleRepository,
                                   UserRepository userRepository) {
        this.mechanicRepository = mechanicRepository;
        this.vehicleRepository = vehicleRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedMechanic("Georgi Ivanov", Specialty.ENGINE, "65.00", LocalDate.of(2015, 3, 2));
        seedMechanic("Nikolay Petrov", Specialty.TRANSMISSION, "70.00", LocalDate.of(2017, 9, 18));
        seedMechanic("Dimitar Stoyanov", Specialty.ELECTRICAL, "60.00", LocalDate.of(2019, 1, 14));
        seedMechanic("Vasil Marinov", Specialty.SUSPENSION, "55.00", LocalDate.of(2020, 6, 8));
        seedMechanic("Stefan Angelov", Specialty.BODYWORK, "50.00", LocalDate.of(2021, 11, 22));
        seedMechanic("Plamen Kolev", Specialty.DIAGNOSTICS, "75.00", LocalDate.of(2013, 4, 5));

        userRepository.findByUsername("customer").ifPresent(owner -> {
            seedVehicle(owner, "CB 4521 KA", "WVWZZZ1KZGW123456", "Volkswagen", "Golf", 2016, 148000);
            seedVehicle(owner, "CA 8890 MP", "JTDBR32E730112233", "Toyota", "Corolla", 2019, 62000);
        });
    }

    private void seedMechanic(String fullName, Specialty specialty, String hourlyRate, LocalDate hiredOn) {
        if (mechanicRepository.existsByFullNameIgnoreCase(fullName)) {
            return;
        }

        Mechanic mechanic = new Mechanic();
        mechanic.setFullName(fullName);
        mechanic.setSpecialty(specialty);
        mechanic.setHourlyRate(new BigDecimal(hourlyRate));
        mechanic.setHiredOn(hiredOn);
        mechanic.setActive(true);

        mechanicRepository.save(mechanic);
        log.info("Seeded mechanic '{}' ({})", fullName, specialty);
    }

    private void seedVehicle(User owner,
                             String plate,
                             String vin,
                             String make,
                             String model,
                             int modelYear,
                             int mileage) {
        if (vehicleRepository.existsByPlateIgnoreCase(plate)) {
            return;
        }

        Vehicle vehicle = new Vehicle();
        vehicle.setPlate(plate);
        vehicle.setVin(vin);
        vehicle.setMake(make);
        vehicle.setModel(model);
        vehicle.setModelYear(modelYear);
        vehicle.setMileage(mileage);
        vehicle.setActive(true);
        vehicle.setRegisteredAt(LocalDateTime.now());
        vehicle.setOwner(owner);

        vehicleRepository.save(vehicle);
        log.info("Seeded vehicle {} for '{}'", plate, owner.getUsername());
    }
}
