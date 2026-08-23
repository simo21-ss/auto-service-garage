package bg.softuni.garage.mechanic;

import bg.softuni.garage.TestFixtures;
import bg.softuni.garage.common.exception.BusinessRuleException;
import bg.softuni.garage.common.exception.DuplicateResourceException;
import bg.softuni.garage.common.exception.ResourceNotFoundException;
import bg.softuni.garage.mechanic.dto.MechanicRequest;
import bg.softuni.garage.repairorder.RepairOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
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
class MechanicServiceImplTest {

    @Mock
    private MechanicRepository mechanicRepository;

    @Mock
    private RepairOrderRepository repairOrderRepository;

    @InjectMocks
    private MechanicServiceImpl mechanicService;

    @Test
    void findAllAndFindActiveDelegateToTheRepository() {
        when(mechanicRepository.findAllByOrderByFullNameAsc())
                .thenReturn(List.of(TestFixtures.mechanic("Vasil", Specialty.SUSPENSION)));
        when(mechanicRepository.findAllByActiveTrueOrderByFullNameAsc())
                .thenReturn(List.of(TestFixtures.mechanic("Vasil", Specialty.SUSPENSION)));

        assertThat(mechanicService.findAll()).hasSize(1);
        assertThat(mechanicService.findActive()).hasSize(1);
    }

    @Test
    void getByIdThrowsForAnUnknownMechanic() {
        UUID id = UUID.randomUUID();
        when(mechanicRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mechanicService.getById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createStoresTheMechanic() {
        when(mechanicRepository.existsByFullNameIgnoreCase("Vasil Marinov")).thenReturn(false);
        when(mechanicRepository.save(any(Mechanic.class))).thenAnswer(call -> call.getArgument(0));

        Mechanic created = mechanicService.create(request("  Vasil Marinov  ", true));

        assertThat(created.getFullName()).isEqualTo("Vasil Marinov");
        assertThat(created.getSpecialty()).isEqualTo(Specialty.SUSPENSION);
        assertThat(created.isActive()).isTrue();
    }

    @Test
    void createRejectsADuplicateName() {
        when(mechanicRepository.existsByFullNameIgnoreCase("Vasil Marinov")).thenReturn(true);

        assertThatThrownBy(() -> mechanicService.create(request("Vasil Marinov", true)))
                .isInstanceOf(DuplicateResourceException.class);

        verify(mechanicRepository, never()).save(any());
    }

    @Test
    void updateAppliesTheNewValues() {
        Mechanic mechanic = TestFixtures.mechanic("Vasil Marinov", Specialty.SUSPENSION);
        when(mechanicRepository.findById(mechanic.getId())).thenReturn(Optional.of(mechanic));
        when(mechanicRepository.save(any(Mechanic.class))).thenAnswer(call -> call.getArgument(0));

        Mechanic updated = mechanicService.update(mechanic.getId(), request("Vasil Marinov", true));

        assertThat(updated.getHourlyRate()).isEqualByComparingTo("65.00");
    }

    @Test
    void updateRejectsRenamingOntoAnotherMechanic() {
        Mechanic mechanic = TestFixtures.mechanic("Vasil Marinov", Specialty.SUSPENSION);
        when(mechanicRepository.findById(mechanic.getId())).thenReturn(Optional.of(mechanic));
        when(mechanicRepository.existsByFullNameIgnoreCase("Georgi Ivanov")).thenReturn(true);

        assertThatThrownBy(() -> mechanicService.update(mechanic.getId(), request("Georgi Ivanov", true)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void deactivatingIsBlockedWhileOpenWorkExists() {
        Mechanic mechanic = TestFixtures.mechanic("Vasil Marinov", Specialty.SUSPENSION);
        when(mechanicRepository.findById(mechanic.getId())).thenReturn(Optional.of(mechanic));
        when(repairOrderRepository.existsByMechanicAndStatusIn(any(Mechanic.class), anyCollection()))
                .thenReturn(true);

        assertThatThrownBy(() -> mechanicService.update(mechanic.getId(), request("Vasil Marinov", false)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be deactivated");
    }

    @Test
    void aMechanicWithOpenWorkCannotBeRemoved() {
        Mechanic mechanic = TestFixtures.mechanic("Vasil Marinov", Specialty.SUSPENSION);
        when(mechanicRepository.findById(mechanic.getId())).thenReturn(Optional.of(mechanic));
        when(repairOrderRepository.existsByMechanicAndStatusIn(any(Mechanic.class), anyCollection()))
                .thenReturn(true);

        assertThatThrownBy(() -> mechanicService.delete(mechanic.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("open repair orders");
    }

    @Test
    void aMechanicWithCompletedHistoryCanOnlyBeDeactivated() {
        Mechanic mechanic = TestFixtures.mechanic("Vasil Marinov", Specialty.SUSPENSION);
        when(mechanicRepository.findById(mechanic.getId())).thenReturn(Optional.of(mechanic));
        when(repairOrderRepository.existsByMechanicAndStatusIn(any(Mechanic.class), anyCollection()))
                .thenReturn(false);
        when(repairOrderRepository.existsByMechanic(mechanic)).thenReturn(true);

        assertThatThrownBy(() -> mechanicService.delete(mechanic.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("can only be deactivated");

        verify(mechanicRepository, never()).delete(any());
    }

    @Test
    void aMechanicWithNoHistoryIsRemoved() {
        Mechanic mechanic = TestFixtures.mechanic("Vasil Marinov", Specialty.SUSPENSION);
        when(mechanicRepository.findById(mechanic.getId())).thenReturn(Optional.of(mechanic));
        when(repairOrderRepository.existsByMechanicAndStatusIn(any(Mechanic.class), anyCollection()))
                .thenReturn(false);
        when(repairOrderRepository.existsByMechanic(mechanic)).thenReturn(false);

        mechanicService.delete(mechanic.getId());

        verify(mechanicRepository).delete(mechanic);
    }

    private MechanicRequest request(String fullName, boolean active) {
        MechanicRequest request = new MechanicRequest();
        request.setFullName(fullName);
        request.setSpecialty(Specialty.SUSPENSION);
        request.setHourlyRate(new BigDecimal("65.00"));
        request.setHiredOn(LocalDate.of(2020, 6, 8));
        request.setActive(active);
        return request;
    }
}
