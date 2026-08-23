package bg.softuni.partssvc.part;

import bg.softuni.partssvc.TestFixtures;
import bg.softuni.partssvc.common.exception.DuplicateSkuException;
import bg.softuni.partssvc.common.exception.PartNotFoundException;
import bg.softuni.partssvc.ledger.StockLedgerService;
import bg.softuni.partssvc.part.dto.PartResponse;
import bg.softuni.partssvc.part.dto.PartUpsertRequest;
import bg.softuni.partssvc.part.dto.RestockRequest;
import bg.softuni.partssvc.supplier.SupplierService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartServiceImplTest {

    @Mock
    private PartRepository partRepository;

    @Mock
    private SupplierService supplierService;

    @Mock
    private StockLedgerService stockLedgerService;

    @InjectMocks
    private PartServiceImpl partService;

    @Test
    void findAllMapsEveryPartOntoAResponse() {
        when(partRepository.findAllByOrderByNameAsc())
                .thenReturn(List.of(TestFixtures.part("BRK-1", 10, 2, 5)));

        List<PartResponse> responses = partService.findAll();

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().sku()).isEqualTo("BRK-1");
        assertThat(responses.getFirst().quantityAvailable()).isEqualTo(8);
    }

    @Test
    void findByCategoryDelegatesToTheRepository() {
        when(partRepository.findAllByCategoryOrderByNameAsc(PartCategory.BRAKES))
                .thenReturn(List.of(TestFixtures.part("BRK-2", 4, 0, 1)));

        assertThat(partService.findByCategory(PartCategory.BRAKES)).hasSize(1);
    }

    @Test
    void findBelowReorderLevelFlagsPartsThatNeedRestocking() {
        when(partRepository.findBelowReorderLevel())
                .thenReturn(List.of(TestFixtures.part("BRK-3", 6, 4, 5)));

        List<PartResponse> responses = partService.findBelowReorderLevel();

        assertThat(responses.getFirst().belowReorderLevel()).isTrue();
    }

    @Test
    void getEntityBySkuThrowsWhenTheSkuIsUnknown() {
        when(partRepository.findBySkuIgnoreCase("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> partService.getEntityBySku("NOPE"))
                .isInstanceOf(PartNotFoundException.class)
                .hasMessageContaining("NOPE");
    }

    @Test
    void getByIdThrowsWhenTheIdIsUnknown() {
        UUID id = UUID.randomUUID();
        when(partRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> partService.getById(id))
                .isInstanceOf(PartNotFoundException.class);
    }

    @Test
    void getByIdReturnsAResponseForAKnownPart() {
        Part part = TestFixtures.part("BRK-4", 12, 0, 3);
        when(partRepository.findById(part.getId())).thenReturn(Optional.of(part));

        assertThat(partService.getById(part.getId()).sku()).isEqualTo("BRK-4");
    }

    @Test
    void createRejectsADuplicateSku() {
        when(partRepository.existsBySkuIgnoreCase("BRK-1")).thenReturn(true);

        assertThatThrownBy(() -> partService.create(upsertRequest("BRK-1"), "admin"))
                .isInstanceOf(DuplicateSkuException.class);

        verify(partRepository, never()).save(any());
    }

    @Test
    void createStoresThePartAndRecordsOpeningStock() {
        when(partRepository.existsBySkuIgnoreCase("BRK-9")).thenReturn(false);
        when(supplierService.getByName("Bosch Bulgaria")).thenReturn(TestFixtures.supplier("Bosch Bulgaria"));
        when(partRepository.save(any(Part.class))).thenAnswer(call -> call.getArgument(0));

        PartResponse created = partService.create(upsertRequest("BRK-9"), "admin");

        assertThat(created.sku()).isEqualTo("BRK-9");
        assertThat(created.quantityOnHand()).isEqualTo(25);
        verify(stockLedgerService).record("BRK-9", "OPENING_STOCK", "admin", 25, 0);
    }

    @Test
    void createUppercasesAndTrimsTheSku() {
        when(partRepository.existsBySkuIgnoreCase("BRK-9")).thenReturn(false);
        when(supplierService.getByName(anyString())).thenReturn(TestFixtures.supplier("Bosch Bulgaria"));
        when(partRepository.save(any(Part.class))).thenAnswer(call -> call.getArgument(0));

        PartResponse created = partService.create(new PartUpsertRequest(" brk-9 ", "Disc",
                PartCategory.BRAKES, new BigDecimal("10.00"), 25, 5, 20, "Bosch Bulgaria"), "admin");

        assertThat(created.sku()).isEqualTo("BRK-9");
    }

    @Test
    void updateRejectsRenamingOntoAnExistingSku() {
        Part part = TestFixtures.part("BRK-1", 10, 0, 2);
        when(partRepository.findById(part.getId())).thenReturn(Optional.of(part));
        when(partRepository.existsBySkuIgnoreCase("BRK-2")).thenReturn(true);

        assertThatThrownBy(() -> partService.update(part.getId(), upsertRequest("BRK-2"), "admin"))
                .isInstanceOf(DuplicateSkuException.class);
    }

    @Test
    void updateAppliesTheNewValues() {
        Part part = TestFixtures.part("BRK-1", 10, 0, 2);
        when(partRepository.findById(part.getId())).thenReturn(Optional.of(part));
        when(supplierService.getByName(anyString())).thenReturn(TestFixtures.supplier("Febi Bilstein"));
        when(partRepository.save(any(Part.class))).thenAnswer(call -> call.getArgument(0));

        PartResponse updated = partService.update(part.getId(), upsertRequest("BRK-1"), "admin");

        assertThat(updated.reorderLevel()).isEqualTo(5);
        assertThat(updated.supplierName()).isEqualTo("Febi Bilstein");
    }

    @Test
    void restockIncreasesOnHandAndWritesTheLedger() {
        Part part = TestFixtures.part("BRK-1", 10, 0, 2);
        when(partRepository.findById(part.getId())).thenReturn(Optional.of(part));
        when(partRepository.save(any(Part.class))).thenAnswer(call -> call.getArgument(0));

        PartResponse restocked = partService.restock(part.getId(), new RestockRequest(15, "delivery"), "admin");

        assertThat(restocked.quantityOnHand()).isEqualTo(25);

        ArgumentCaptor<Integer> before = ArgumentCaptor.forClass(Integer.class);
        verify(stockLedgerService).record(anyString(), anyString(), anyString(), anyInt(), before.capture());
        assertThat(before.getValue()).isEqualTo(10);
    }

    private PartUpsertRequest upsertRequest(String sku) {
        return new PartUpsertRequest(sku, "Front brake disc", PartCategory.BRAKES,
                new BigDecimal("78.50"), 25, 5, 20, "Bosch Bulgaria");
    }
}
