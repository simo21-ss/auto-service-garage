package bg.softuni.garage.vehicle;

import bg.softuni.garage.TestFixtures;
import bg.softuni.garage.common.exception.BusinessRuleException;
import bg.softuni.garage.common.exception.DuplicateResourceException;
import bg.softuni.garage.common.exception.ResourceNotFoundException;
import bg.softuni.garage.repairorder.RepairOrderRepository;
import bg.softuni.garage.user.RoleName;
import bg.softuni.garage.user.User;
import bg.softuni.garage.user.UserService;
import bg.softuni.garage.vehicle.dto.VehicleRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleServiceImplTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private RepairOrderRepository repairOrderRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private VehicleServiceImpl vehicleService;

    @Test
    void registerNormalisesThePlateAndVin() {
        User owner = TestFixtures.user("ivan", RoleName.CUSTOMER);
        when(vehicleRepository.existsByPlateIgnoreCase("CB 1234 AB")).thenReturn(false);
        when(userService.getById(owner.getId())).thenReturn(owner);
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(call -> call.getArgument(0));

        Vehicle registered = vehicleService.register(request(" cb 1234 ab ", "wvwzzz1kzgw123456", 90000),
                owner.getId());

        assertThat(registered.getPlate()).isEqualTo("CB 1234 AB");
        assertThat(registered.getVin()).isEqualTo("WVWZZZ1KZGW123456");
        assertThat(registered.isActive()).isTrue();
        assertThat(registered.getOwner()).isEqualTo(owner);
    }

    @Test
    void registerRejectsADuplicatePlate() {
        when(vehicleRepository.existsByPlateIgnoreCase("CB 1234 AB")).thenReturn(true);

        assertThatThrownBy(() -> vehicleService.register(
                request("CB 1234 AB", "WVWZZZ1KZGW123456", 90000), UUID.randomUUID()))
                .isInstanceOf(DuplicateResourceException.class);

        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void findOwnedByReturnsTheOwnersVehicles() {
        User owner = TestFixtures.user("ivan", RoleName.CUSTOMER);
        when(userService.getById(owner.getId())).thenReturn(owner);
        when(vehicleRepository.findAllByOwnerOrderByRegisteredAtDesc(owner))
                .thenReturn(List.of(TestFixtures.vehicle("CB 1234 AB", owner)));

        assertThat(vehicleService.findOwnedBy(owner.getId())).hasSize(1);
    }

    @Test
    void findBookableForReturnsOnlyActiveVehicles() {
        User owner = TestFixtures.user("ivan", RoleName.CUSTOMER);
        when(userService.getById(owner.getId())).thenReturn(owner);
        when(vehicleRepository.findAllByOwnerAndActiveTrueOrderByPlateAsc(owner))
                .thenReturn(List.of(TestFixtures.vehicle("CB 1234 AB", owner)));

        assertThat(vehicleService.findBookableFor(owner.getId())).hasSize(1);
    }

    @Test
    void aVehicleBelongingToSomeoneElseLooksMissing() {
        User owner = TestFixtures.user("ivan", RoleName.CUSTOMER);
        Vehicle vehicle = TestFixtures.vehicle("CB 1234 AB", owner);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> vehicleService.getOwnedById(vehicle.getId(), UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getByIdThrowsForAnUnknownVehicle() {
        UUID id = UUID.randomUUID();
        when(vehicleRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehicleService.getById(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateRefusesToRollBackTheMileage() {
        User owner = TestFixtures.user("ivan", RoleName.CUSTOMER);
        Vehicle vehicle = TestFixtures.vehicle("CB 1234 AB", owner);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> vehicleService.update(vehicle.getId(),
                request("CB 1234 AB", "WVWZZZ1KZGW123456", 5000), owner.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be lower");
    }

    @Test
    void updateAcceptsAHigherMileage() {
        User owner = TestFixtures.user("ivan", RoleName.CUSTOMER);
        Vehicle vehicle = TestFixtures.vehicle("CB 1234 AB", owner);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(call -> call.getArgument(0));

        Vehicle updated = vehicleService.update(vehicle.getId(),
                request("CB 1234 AB", "WVWZZZ1KZGW123456", 150000), owner.getId());

        assertThat(updated.getMileage()).isEqualTo(150000);
    }

    @Test
    void updateRejectsAPlateOwnedByAnotherVehicle() {
        User owner = TestFixtures.user("ivan", RoleName.CUSTOMER);
        Vehicle vehicle = TestFixtures.vehicle("CB 1234 AB", owner);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.existsByPlateIgnoreCase("CB 9999 ZZ")).thenReturn(true);

        assertThatThrownBy(() -> vehicleService.update(vehicle.getId(),
                request("CB 9999 ZZ", "WVWZZZ1KZGW123456", 150000), owner.getId()))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void retiringIsBlockedWhileOpenOrdersExist() {
        User owner = TestFixtures.user("ivan", RoleName.CUSTOMER);
        Vehicle vehicle = TestFixtures.vehicle("CB 1234 AB", owner);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(repairOrderRepository.existsByVehicleAndStatusIn(any(Vehicle.class), anyCollection()))
                .thenReturn(true);

        assertThatThrownBy(() -> vehicleService.setActive(vehicle.getId(), false, owner.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("open repair orders");
    }

    @Test
    void retiringSucceedsWhenNothingIsOpen() {
        User owner = TestFixtures.user("ivan", RoleName.CUSTOMER);
        Vehicle vehicle = TestFixtures.vehicle("CB 1234 AB", owner);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(repairOrderRepository.existsByVehicleAndStatusIn(any(Vehicle.class), anyCollection()))
                .thenReturn(false);
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(call -> call.getArgument(0));

        assertThat(vehicleService.setActive(vehicle.getId(), false, owner.getId()).isActive()).isFalse();
    }

    @Test
    void togglingToTheCurrentStateIsRejected() {
        User owner = TestFixtures.user("ivan", RoleName.CUSTOMER);
        Vehicle vehicle = TestFixtures.vehicle("CB 1234 AB", owner);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> vehicleService.setActive(vehicle.getId(), true, owner.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already");
    }

    @Test
    void aVehicleWithHistoryCannotBeDeleted() {
        User owner = TestFixtures.user("ivan", RoleName.CUSTOMER);
        Vehicle vehicle = TestFixtures.vehicle("CB 1234 AB", owner);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(repairOrderRepository.existsByVehicle(vehicle)).thenReturn(true);

        assertThatThrownBy(() -> vehicleService.delete(vehicle.getId(), owner.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("repair history");

        verify(vehicleRepository, never()).delete(any());
    }

    @Test
    void aVehicleWithNoHistoryIsDeleted() {
        User owner = TestFixtures.user("ivan", RoleName.CUSTOMER);
        Vehicle vehicle = TestFixtures.vehicle("CB 1234 AB", owner);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(repairOrderRepository.existsByVehicle(vehicle)).thenReturn(false);

        vehicleService.delete(vehicle.getId(), owner.getId());

        verify(vehicleRepository).delete(vehicle);
    }

    @Test
    void countOwnedByDelegatesToTheRepository() {
        User owner = TestFixtures.user("ivan", RoleName.CUSTOMER);
        when(vehicleRepository.countByOwner(owner)).thenReturn(2L);

        assertThat(vehicleService.countOwnedBy(owner)).isEqualTo(2L);
    }

    private VehicleRequest request(String plate, String vin, int mileage) {
        VehicleRequest request = new VehicleRequest();
        request.setPlate(plate);
        request.setVin(vin);
        request.setMake("Volkswagen");
        request.setModel("Golf");
        request.setModelYear(2016);
        request.setMileage(mileage);
        return request;
    }
}
