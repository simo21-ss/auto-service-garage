package bg.softuni.partssvc.supplier;

import bg.softuni.partssvc.TestFixtures;
import bg.softuni.partssvc.common.exception.PartNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierServiceImplTest {

    @Mock
    private SupplierRepository supplierRepository;

    @InjectMocks
    private SupplierServiceImpl supplierService;

    @Test
    void findActiveMapsSuppliersOntoResponses() {
        when(supplierRepository.findAllByActiveTrueOrderByNameAsc())
                .thenReturn(List.of(TestFixtures.supplier("Bosch Bulgaria")));

        assertThat(supplierService.findActive())
                .singleElement()
                .satisfies(supplier -> {
                    assertThat(supplier.name()).isEqualTo("Bosch Bulgaria");
                    assertThat(supplier.active()).isTrue();
                    assertThat(supplier.leadTimeDays()).isEqualTo(5);
                });
    }

    @Test
    void getByNameReturnsTheSupplier() {
        when(supplierRepository.findByNameIgnoreCase("Bosch Bulgaria"))
                .thenReturn(Optional.of(TestFixtures.supplier("Bosch Bulgaria")));

        assertThat(supplierService.getByName("Bosch Bulgaria").getName()).isEqualTo("Bosch Bulgaria");
    }

    @Test
    void getByNameThrowsForAnUnknownSupplier() {
        when(supplierRepository.findByNameIgnoreCase("Nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> supplierService.getByName("Nope"))
                .isInstanceOf(PartNotFoundException.class)
                .hasMessageContaining("Nope");
    }
}
