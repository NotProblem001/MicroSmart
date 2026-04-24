const express = require('express');
const amqp = require('amqplib');
const CircuitBreaker = require('opossum');

const app = express();
app.use(express.json());
const PORT = process.env.PORT || 3002;

// Función que simula el procesamiento de una solicitud a un servicio de pago/logística externo
async function procesarSolicitudExterna(reserva) {
    // Simulación de una llamada inestable (30% de probabilidad de fallo aleatorio)
    if (Math.random() < 0.3) {
        throw new Error('Fallo temporal en el servicio externo de procesamiento de solicitudes');
    }
    // Lógica de procesamiento de la solicitud...
    return { success: true, reservaId: reserva.id, estado: 'APROBADA' };
}

// Configuración de Circuit Breaker
const breakerOptions = {
    timeout: 3000, // Si toma más de 3 segundos, dispara un fallo
    errorThresholdPercentage: 50, // Umbral de error del 50%
    resetTimeout: 10000 // Esperar 10 segundos antes de intentar cerrar el circuito de nuevo
};

const breaker = new CircuitBreaker(procesarSolicitudExterna, breakerOptions);

// Fallback cuando el circuito está abierto
breaker.fallback(() => ({ 
    success: false, 
    estado: 'RECHAZADA_POR_SISTEMA', 
    msg: 'Circuit Breaker Abierto: El servicio de procesamiento no está disponible actualmente. Intentando de nuevo más tarde.' 
}));

breaker.on('open', () => console.log('🛑 Circuit Breaker: ABIERTO (Servicio externo fallando)'));
breaker.on('halfOpen', () => console.log('⚠️ Circuit Breaker: MEDIO ABIERTO (Probando servicio externo)'));
breaker.on('close', () => console.log('✅ Circuit Breaker: CERRADO (Servicio externo normalizado)'));

// Patrón Consumer para eventos de RabbitMQ
async function connectRabbitMQ() {
    try {
        const connection = await amqp.connect(process.env.RABBITMQ_URL || 'amqp://user:password@localhost:5672');
        const channel = await connection.createChannel();
        await channel.assertQueue('reservas_creadas');
        
        console.log('Conectado a RabbitMQ - MS-Solicitudes (Consumidor)');
        
        channel.consume('reservas_creadas', async (msg) => {
            if (msg !== null) {
                const reserva = JSON.parse(msg.content.toString());
                console.log(`Mensaje recibido para procesar solicitud: Reserva ${reserva.id}`);
                
                // Procesar la solicitud usando el Circuit Breaker
                const resultado = await breaker.fire(reserva);
                console.log('Resultado del procesamiento de solicitud:', resultado);
                
                // Confirmar que el mensaje fue procesado correctamente
                channel.ack(msg);
            }
        });
    } catch (error) {
        console.error('Error conectando a RabbitMQ:', error);
    }
}
connectRabbitMQ();

app.get('/health', (req, res) => res.send('MS-Solicitudes OK'));

app.listen(PORT, () => {
    console.log(`MS-Solicitudes escuchando en el puerto ${PORT}`);
});
