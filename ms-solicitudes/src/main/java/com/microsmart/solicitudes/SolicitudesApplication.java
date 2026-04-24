package com.microsmart.solicitudes;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.persistence.*;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@SpringBootApplication
public class SolicitudesApplication {
    public static void main(String[] args) {
        SpringApplication.run(SolicitudesApplication.class, args);
    }
}

// Consumer de RabbitMQ
@Service
class ReservaConsumer {
    private final SolicitudService solicitudService;

    public ReservaConsumer(SolicitudService solicitudService) {
        this.solicitudService = solicitudService;
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "solicitudes_queue", durable = "true"),
            exchange = @Exchange(value = "reservas_exchange", type = "topic"),
            key = "reserva.creada"
    ))
    public void consumirReservaCreada(ReservaEvent event) {
        System.out.println("Mensaje recibido para reserva: " + event.reservaId);
        solicitudService.procesarSolicitud(event);
    }
}

// Servicio con Circuit Breaker
@Service
class SolicitudService {
    private final SolicitudRepository repository;

    public SolicitudService(SolicitudRepository repository) {
        this.repository = repository;
    }

    @Transactional
    @CircuitBreaker(name = "solicitudBreaker", fallbackMethod = "fallbackProcesarSolicitud")
    public void procesarSolicitud(ReservaEvent event) {
        // Lógica de negocio simulada que podría fallar bajo carga
        if (Math.random() < 0.3) {
            throw new RuntimeException("Fallo simulado en el procesamiento de la solicitud");
        }

        Solicitud s = new Solicitud();
        s.setReservaId(event.reservaId);
        s.setEstado("APROBADA");
        repository.save(s);
        System.out.println("Solicitud aprobada para reserva: " + event.reservaId);
    }

    // Fallback del Circuit Breaker
    public void fallbackProcesarSolicitud(ReservaEvent event, Throwable t) {
        System.err.println("Circuit Breaker ABIERTO/FALLBACK. Error al procesar reserva " + event.reservaId + ": " + t.getMessage());
        // Aquí se podría guardar en una tabla de reintentos (DLQ) o registrar el fallo
    }
}

interface SolicitudRepository extends JpaRepository<Solicitud, Long> {}

@Entity
class Solicitud {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long reservaId;
    private String estado;
    // Getters y Setters
    public void setReservaId(Long reservaId) { this.reservaId = reservaId; }
    public void setEstado(String estado) { this.estado = estado; }
}

class ReservaEvent implements java.io.Serializable {
    public Long reservaId;
    public Long itemId;
    public Integer cantidad;
}
