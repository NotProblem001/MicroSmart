const express = require('express');
const { Pool } = require('pg');
const amqp = require('amqplib');

const app = express();
app.use(express.json());
const PORT = process.env.PORT || 3001;

// Database-per-Service: PostgreSQL
// Principio de menor privilegio: el usuario ms_user solo debería tener permisos sobre las tablas que usa
const pool = new Pool({
    user: process.env.DB_USER,
    host: process.env.DB_HOST,
    database: process.env.DB_NAME,
    password: process.env.DB_PASSWORD,
    port: process.env.DB_PORT,
});

let channel;
// Event-Driven: Publicador de RabbitMQ
async function connectRabbitMQ() {
    try {
        const connection = await amqp.connect(process.env.RABBITMQ_URL || 'amqp://user:password@localhost:5672');
        channel = await connection.createChannel();
        await channel.assertQueue('reservas_creadas');
        console.log('Conectado a RabbitMQ - MS-Reservas');
    } catch (error) {
        console.error('Error conectando a RabbitMQ:', error);
    }
}
connectRabbitMQ();

app.post('/reservas', async (req, res) => {
    const client = await pool.connect();
    try {
        const { inventarioId, cantidad, usuarioId } = req.body;
        
        await client.query('BEGIN'); // Iniciar transacción
        
        // Bloqueo optimista (FOR UPDATE) - Evita condiciones de carrera en alta concurrencia
        const result = await client.query(
            'SELECT id, stock FROM inventario WHERE id = $1 FOR UPDATE',
            [inventarioId]
        );

        if (result.rows.length === 0 || result.rows[0].stock < cantidad) {
            await client.query('ROLLBACK');
            return res.status(400).json({ error: 'Stock insuficiente o inventario no encontrado' });
        }

        // Actualizar stock
        await client.query(
            'UPDATE inventario SET stock = stock - $1 WHERE id = $2',
            [cantidad, inventarioId]
        );

        // Crear reserva
        const reservaResult = await client.query(
            'INSERT INTO reservas (inventario_id, cantidad, usuario_id, estado) VALUES ($1, $2, $3, $4) RETURNING *',
            [inventarioId, cantidad, usuarioId, 'CREADA']
        );

        await client.query('COMMIT'); // Confirmar transacción

        const nuevaReserva = reservaResult.rows[0];

        // Publicación de eventos (Event-Driven / Saga)
        if (channel) {
            channel.sendToQueue('reservas_creadas', Buffer.from(JSON.stringify(nuevaReserva)));
            console.log(`Evento publicado: reserva_creada ID ${nuevaReserva.id}`);
        }

        res.status(201).json(nuevaReserva);
    } catch (error) {
        await client.query('ROLLBACK');
        console.error('Error al procesar reserva:', error);
        res.status(500).json({ error: 'Error interno del servidor' });
    } finally {
        client.release();
    }
});

app.listen(PORT, () => {
    console.log(`MS-Reservas escuchando en el puerto ${PORT}`);
});
