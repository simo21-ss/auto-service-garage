package bg.softuni.partssvc.part;

import bg.softuni.partssvc.supplier.Supplier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "parts")
@Getter
@Setter
public class Part {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Size(min = 3, max = 24)
    @Column(nullable = false, unique = true, length = 24)
    private String sku;

    @NotBlank
    @Size(min = 3, max = 100)
    @Column(nullable = false, length = 100)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PartCategory category;

    @NotNull
    @DecimalMin("0.01")
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Min(0)
    @Max(100_000)
    @Column(name = "quantity_on_hand", nullable = false)
    private int quantityOnHand;

    @Min(0)
    @Max(100_000)
    @Column(name = "quantity_reserved", nullable = false)
    private int quantityReserved;

    @Min(0)
    @Max(10_000)
    @Column(name = "reorder_level", nullable = false)
    private int reorderLevel;

    @Min(1)
    @Max(10_000)
    @Column(name = "reorder_quantity", nullable = false)
    private int reorderQuantity;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    public int availableQuantity() {
        return quantityOnHand - quantityReserved;
    }
}
