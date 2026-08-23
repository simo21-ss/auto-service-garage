package bg.softuni.garage;

import bg.softuni.garage.mechanic.Mechanic;
import bg.softuni.garage.mechanic.Specialty;
import bg.softuni.garage.repairorder.RepairOrder;
import bg.softuni.garage.repairorder.RepairOrderStatus;
import bg.softuni.garage.repairorder.ServiceTask;
import bg.softuni.garage.repairorder.ServiceTaskStatus;
import bg.softuni.garage.user.Permission;
import bg.softuni.garage.user.PermissionName;
import bg.softuni.garage.user.Role;
import bg.softuni.garage.user.RoleName;
import bg.softuni.garage.user.User;
import bg.softuni.garage.vehicle.Vehicle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class TestFixtures {

    private TestFixtures() {
    }

    public static Role role(RoleName name, PermissionName... permissions) {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setName(name);
        Set<Permission> granted = Arrays.stream(permissions)
                .map(TestFixtures::permission)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        role.setPermissions(granted);
        return role;
    }

    public static Permission permission(PermissionName name) {
        Permission permission = new Permission();
        permission.setId(UUID.randomUUID());
        permission.setName(name);
        return permission;
    }

    public static User user(String username, RoleName roleName) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setEmail(username + "@garage.bg");
        user.setPassword("$2a$10$hashed");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setPhone("+359 88 000 0000");
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setRole(role(roleName, PermissionName.VEHICLE_MANAGE, PermissionName.ORDER_BOOK));
        return user;
    }

    public static Vehicle vehicle(String plate, User owner) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(UUID.randomUUID());
        vehicle.setPlate(plate);
        vehicle.setVin("WVWZZZ1KZGW123456");
        vehicle.setMake("Volkswagen");
        vehicle.setModel("Golf");
        vehicle.setModelYear(2016);
        vehicle.setMileage(120000);
        vehicle.setActive(true);
        vehicle.setRegisteredAt(LocalDateTime.now());
        vehicle.setOwner(owner);
        return vehicle;
    }

    public static Mechanic mechanic(String name, Specialty specialty) {
        Mechanic mechanic = new Mechanic();
        mechanic.setId(UUID.randomUUID());
        mechanic.setFullName(name);
        mechanic.setSpecialty(specialty);
        mechanic.setHourlyRate(new BigDecimal("55.00"));
        mechanic.setActive(true);
        mechanic.setHiredOn(LocalDate.of(2020, 1, 1));
        return mechanic;
    }

    public static RepairOrder order(Vehicle vehicle, RepairOrderStatus status, Specialty specialty) {
        RepairOrder order = new RepairOrder();
        order.setId(UUID.randomUUID());
        order.setReference("RO-2026-0001");
        order.setVehicle(vehicle);
        order.setComplaint("Something is making a noise at the front");
        order.setRequiredSpecialty(specialty);
        order.setStatus(status);
        order.setCreatedAt(LocalDateTime.now());
        order.setLabourCost(BigDecimal.ZERO);
        order.setPartsCost(BigDecimal.ZERO);
        return order;
    }

    public static ServiceTask task(RepairOrder order, ServiceTaskStatus status, String hours) {
        ServiceTask task = new ServiceTask();
        task.setId(UUID.randomUUID());
        task.setRepairOrder(order);
        task.setOperation("Replace front brake discs");
        task.setHours(new BigDecimal(hours));
        task.setHourlyRate(new BigDecimal("55.00"));
        task.setStatus(status);
        task.setCreatedAt(LocalDateTime.now());
        return task;
    }
}
