package com.microsmart.reservas;

import jakarta.persistence.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Optional;

@SpringBootApplication
public class ReservasApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReservasApplication.class, args);
    }
}

// Controladores
@RestController
@RequestMapping("/api/reservas")
class ReservaController {
    private final ReservaService service;
    public ReservaController(ReservaService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<?> crearReserva(@RequestBody ReservaRequest req) {
        service.crearReserva(req);
        return ResponseEntity.status(201).body("Reserva creada");
    }
}

// Servicio
@Service
class ReservaService {
    private final InventarioRepository invRepo;
    private final ReservaRepository resRepo;
    private final RabbitTemplate rabbitTemplate;

    public ReservaService(InventarioRepository invRepo, ReservaRepository resRepo, RabbitTemplate rabbitTemplate) {
        this.invRepo = invRepo; this.resRepo = resRepo; this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    public void crearReserva(ReservaRequest req) {
        // Bloqueo pesimista: FOR UPDATE en PostgreSQL
        Inventario inv = invRepo.findByIdForUpdate(req.getItemId())
            .orElseThrow(() -> new RuntimeException("Item no encontrado"));
            
        if(inv.getStock() < req.getCantidad()) {
            throw new RuntimeException("Stock insuficiente");
        }
        
        inv.setStock(inv.getStock() - req.getCantidad());
        invRepo.save(inv);

        Reserva r = new Reserva();
        r.setItemId(req.getItemId());
        r.setCantidad(req.getCantidad());
        r.setEstado("PENDIENTE");
        r.setUserId(req.getUserId());
        resRepo.save(r);

        // Patrón Saga / Publicación de evento
        ReservaEvent event = new ReservaEvent(r.getId(), req.getItemId(), req.getCantidad());
        rabbitTemplate.convertAndSend("reservas_exchange", "reserva.creada", event);
    }
}

// Repositorios
interface InventarioRepository extends JpaRepository<Inventario, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) // Genera SELECT ... FOR UPDATE
    @Query("SELECT i FROM Inventario i WHERE i.id = :id")
    Optional<Inventario> findByIdForUpdate(@Param("id") Long id);
}

interface ReservaRepository extends JpaRepository<Reserva, Long> {}

// Entidades y DTOs
@Entity
class Inventario {
    @Id private Long id;
    private Integer stock;
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
}

@Entity
class Reserva {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long itemId;
    private Integer cantidad;
    private String estado;
    private String userId;
    // Getters y Setters...
    public Long getId() { return id; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    public void setEstado(String estado) { this.estado = estado; }
    public void setUserId(String userId) { this.userId = userId; }
}

class ReservaRequest {
    private Long itemId;
    private Integer cantidad;
    private String userId;
    public Long getItemId() { return itemId; }
    public Integer getCantidad() { return cantidad; }
    public String getUserId() { return userId; }
}

class ReservaEvent implements java.io.Serializable {
    public Long reservaId;
    public Long itemId;
    public Integer cantidad;
    public ReservaEvent(Long reservaId, Long itemId, Integer cantidad) {
        this.reservaId = reservaId; this.itemId = itemId; this.cantidad = cantidad;
    }
}
