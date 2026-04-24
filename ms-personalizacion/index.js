const express = require('express');
const mongoose = require('mongoose');

const app = express();
app.use(express.json());
const PORT = process.env.PORT || 3003;

// Database-per-Service: MongoDB
mongoose.connect(process.env.MONGO_URI || 'mongodb://localhost:27017/microsmart_personalizacion', {
    useNewUrlParser: true,
    useUnifiedTopology: true
}).then(() => console.log('Conectado a MongoDB - MS-Personalizacion'))
  .catch(err => console.error('Error conectando a MongoDB:', err));

// ============================================
// PATRÓN CQRS (Command Query Responsibility Segregation)
// ============================================

// --- 1. MODELO DE ESCRITURA (COMMAND: Eventos de Uso) ---
const EventoUsoSchema = new mongoose.Schema({
    usuarioId: String,
    accion: String, // ej: "VER_PRODUCTO", "COMPRAR"
    item: String,
    timestamp: { type: Date, default: Date.now }
});
const EventoUso = mongoose.model('EventoUso', EventoUsoSchema);

// --- 2. MODELO DE LECTURA (QUERY: Recomendaciones Precalculadas) ---
const RecomendacionSchema = new mongoose.Schema({
    usuarioId: String,
    recomendaciones: [String],
    actualizadoEn: { type: Date, default: Date.now }
});
const Recomendacion = mongoose.model('Recomendacion', RecomendacionSchema);

// Endpoint de Escritura (Command) - Registra eventos sin afectar las lecturas en tiempo real
app.post('/eventos', async (req, res) => {
    try {
        const evento = new EventoUso(req.body);
        await evento.save();
        
        // Simulación: Un proceso Worker o Event Handler actualizaría el Modelo de Lectura asíncronamente
        // Ejemplo: actualizarRecomendacionesAsync(evento.usuarioId);
        
        res.status(201).json({ mensaje: 'Evento registrado con éxito' });
    } catch (error) {
        res.status(500).json({ error: 'Error guardando evento' });
    }
});

// Endpoint de Lectura (Query) - Devuelve recomendaciones optimizadas para lectura
app.get('/recomendaciones/:usuarioId', async (req, res) => {
    try {
        // En CQRS, la lectura consulta un modelo pre-procesado, haciendo la respuesta muy rápida
        const recomendacion = await Recomendacion.findOne({ usuarioId: req.params.usuarioId });
        
        if (!recomendacion) return res.status(404).json({ error: 'No hay recomendaciones generadas para este usuario' });

        // Ética y Privacidad: Anonimización de datos
        // Ocultamos el ID real del usuario y enviamos un hash/identificador ofuscado
        const respuestaAnonimizada = {
            usuarioHash: Buffer.from(recomendacion.usuarioId).toString('base64'), // Ejemplo básico de seudonimización
            recomendaciones: recomendacion.recomendaciones,
            actualizadoEn: recomendacion.actualizadoEn,
            nota: "Datos anonimizados para proteger la privacidad del usuario"
        };

        res.json(respuestaAnonimizada);
    } catch (error) {
        res.status(500).json({ error: 'Error obteniendo recomendaciones' });
    }
});

app.listen(PORT, () => {
    console.log(`MS-Personalizacion escuchando en el puerto ${PORT}`);
});
