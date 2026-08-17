package bg.softuni.garage.vehicle;

import bg.softuni.garage.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "vehicles")
@Getter
@Setter
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Size(min = 4, max = 12)
    @Column(nullable = false, unique = true, length = 12)
    private String plate;

    @NotBlank
    @Size(min = 11, max = 17)
    @Column(nullable = false, length = 17)
    private String vin;

    @NotBlank
    @Size(max = 40)
    @Column(nullable = false, length = 40)
    private String make;

    @NotBlank
    @Size(max = 40)
    @Column(nullable = false, length = 40)
    private String model;

    @Min(1950)
    @Max(2100)
    @Column(name = "model_year", nullable = false)
    private int modelYear;

    @Min(0)
    @Max(2_000_000)
    @Column(nullable = false)
    private int mileage;

    @Column(nullable = false)
    private boolean active;

    @NotNull
    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;
}
