package bg.softuni.garage.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_entries")
@Getter
@Setter
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Size(max = 60)
    @Column(nullable = false, length = 60)
    private String action;

    @NotBlank
    @Size(max = 60)
    @Column(nullable = false, length = 60)
    private String actor;

    @Size(max = 500)
    @Column(length = 500)
    private String details;

    @NotNull
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
}
