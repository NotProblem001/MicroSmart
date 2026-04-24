package com.microsmart.personalizacion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;
import java.util.List;

@SpringBootApplication
public class PersonalizacionApplication {
    public static void main(String[] args) {
        SpringApplication.run(PersonalizacionApplication.class, args);
    }
}

// Controladores para CQRS
@RestController
@RequestMapping("/api/personalizacion")
class PersonalizacionController {
    
    private final PersonalizacionService service;

    public PersonalizacionController(PersonalizacionService service) {
        this.service = service;
    }

    // Lado de ESCRITURA (Command)
    @PostMapping("/events/usage")
    public ResponseEntity<?> registrarEventoUso(@RequestBody UsageEventRequest req) {
        service.registrarEvento(req);
        return ResponseEntity.status(201).body("Evento de uso registrado y anonimizado.");
    }

    // Lado de LECTURA (Query)
    @GetMapping("/recommendations/{userId}")
    public ResponseEntity<?> obtenerRecomendaciones(@PathVariable String userId) {
        return ResponseEntity.ok(service.obtenerRecomendaciones(userId));
    }
}

@Service
class PersonalizacionService {
    private final UsageEventWriteRepository writeRepo;
    private final RecommendationReadRepository readRepo;

    public PersonalizacionService(UsageEventWriteRepository writeRepo, RecommendationReadRepository readRepo) {
        this.writeRepo = writeRepo;
        this.readRepo = readRepo;
    }

    // Requerimiento de Seguridad/Ética: Anonimización de datos
    private String hashId(String userId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((userId + "salt_secreto").getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error en hash");
        }
    }

    public void registrarEvento(UsageEventRequest req) {
        // Anonimizar el ID y remover PII (Emails, Nombres completos)
        String anonId = hashId(req.getUserId());
        
        // 1. Guardar evento en colección de escritura (Event Sourcing simple)
        UsageEventWrite event = new UsageEventWrite();
        event.setAnonUserId(anonId);
        event.setAccion(req.getAccion());
        event.setTimestamp(new Date());
        writeRepo.save(event);

        // 2. Proyectar/Actualizar la colección de lectura (CQRS)
        RecommendationRead readModel = readRepo.findByAnonUserId(anonId)
            .orElse(new RecommendationRead(anonId));
        readModel.setInteracciones(readModel.getInteracciones() + 1);
        readRepo.save(readModel);
    }

    public Object obtenerRecomendaciones(String userId) {
        String anonId = hashId(userId);
        RecommendationRead readModel = readRepo.findByAnonUserId(anonId).orElse(null);
        
        if (readModel == null) {
            return "No hay suficientes datos para recomendar.";
        }
        return List.of("Producto Recomendado 1", "Producto Recomendado 2");
    }
}

// Repositorios de MongoDB (Separados por Escritura y Lectura)
interface UsageEventWriteRepository extends MongoRepository<UsageEventWrite, String> {}
interface RecommendationReadRepository extends MongoRepository<RecommendationRead, String> {
    java.util.Optional<RecommendationRead> findByAnonUserId(String anonUserId);
}

// Documentos (Colecciones)
@Document(collection = "usage_events_write")
class UsageEventWrite {
    @Id private String id;
    private String anonUserId;
    private String accion;
    private Date timestamp;
    // Getters / Setters...
    public void setAnonUserId(String anonUserId) { this.anonUserId = anonUserId; }
    public void setAccion(String accion) { this.accion = accion; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
}

@Document(collection = "recommendations_read")
class RecommendationRead {
    @Id private String id;
    private String anonUserId;
    private int interacciones;

    public RecommendationRead(String anonUserId) { this.anonUserId = anonUserId; this.interacciones = 0; }
    public String getAnonUserId() { return anonUserId; }
    public int getInteracciones() { return interacciones; }
    public void setInteracciones(int interacciones) { this.interacciones = interacciones; }
}

// DTO
class UsageEventRequest {
    private String userId;
    private String accion;
    // Getters...
    public String getUserId() { return userId; }
    public String getAccion() { return accion; }
}
